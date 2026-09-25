"""Renders an APK's launcher icon to a small PNG, so the manager can show an app's real icon before
it is installed. Handles plain bitmap icons and adaptive icons whose layers are bitmaps, solid
colors or simple vector drawables (what launcher icons almost always are); anything else (a gradient-only layer, say)
returns None and the manager keeps its lettered placeholder.
"""
import io
import re
import subprocess
import zipfile

import resvg_py
from PIL import Image

ICON_SIZE = 128
# adaptive icon layers are 108dp with the visible icon in the middle 72dp
ADAPTIVE_VISIBLE_FRACTION = 72 / 108
LAYER_SIZE = 432
BITMAP_EXTENSIONS = (".png", ".webp", ".jpg")
DENSITY_ORDER = ["xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi", "anydpi", "nodpi", ""]
ANDROID_NS = "http://schemas.android.com/apk/res/android:"


def _run(aapt2, *args):
    return subprocess.run(
        [aapt2, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", check=True
    ).stdout


def _resource_table(aapt2, apk):
    """resource id -> [(config, value)] from `aapt2 dump resources`."""
    table, current = {}, None
    for line in _run(aapt2, "dump", "resources", str(apk)).splitlines():
        stripped = line.strip()
        if stripped.startswith("resource "):
            current = table.setdefault(stripped.split()[1].lower(), [])
        elif current is not None and stripped.startswith("("):
            config, _, value = stripped[1:].partition(") ")
            current.append((config, value.strip()))
    return table


def _density_rank(config):
    for rank, density in enumerate(DENSITY_ORDER):
        if density and density in config:
            return rank
    return len(DENSITY_ORDER)


def _file_path(value):
    match = re.match(r"\(file\) (\S+)", value)
    return match.group(1) if match else None


class _Renderer:
    def __init__(self, aapt2, apk):
        self.aapt2 = aapt2
        self.apk = apk
        self.zip = zipfile.ZipFile(apk)
        self.table = _resource_table(aapt2, apk)

    def layer(self, value, depth=0):
        """An RGBA image of LAYER_SIZE for a path, reference or color value, or None."""
        if depth > 5 or not value:
            return None
        value = value.strip()
        color = re.fullmatch(r"#([0-9a-fA-F]{6,8})", value)
        if color:
            hex_value = color.group(1).rjust(8, "f")
            argb = int(hex_value, 16)
            rgba = ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255)
            return Image.new("RGBA", (LAYER_SIZE, LAYER_SIZE), rgba)
        reference = re.fullmatch(r"@(0x[0-9a-fA-F]{8})", value)
        if reference:
            entries = self.table.get(reference.group(1).lower(), [])
            entries = sorted(entries, key=lambda entry: _density_rank(entry[0]))
            for _config, entry_value in entries:
                rendered = self.layer(_file_path(entry_value) or entry_value, depth + 1)
                if rendered is not None:
                    return rendered
            return None
        if value.endswith(BITMAP_EXTENSIONS):
            with self.zip.open(value) as handle:
                image = Image.open(io.BytesIO(handle.read())).convert("RGBA")
            return image.resize((LAYER_SIZE, LAYER_SIZE), Image.LANCZOS)
        if value.endswith(".xml"):
            return self.xml_layer(value, depth)
        return None

    def xml_layer(self, path, depth):
        tree = _run(self.aapt2, "dump", "xmltree", str(self.apk), "--file", path)
        root = re.search(r"E: (\S+)", tree)
        if root is None:
            return None
        if root.group(1) == "adaptive-icon":
            return self.adaptive(tree, depth, crop=False)
        if root.group(1) == "vector":
            svg = _vector_to_svg(_parse_xmltree(tree), self.color)
            if svg is None:
                return None
            png = resvg_py.svg_to_bytes(svg_string=svg, width=LAYER_SIZE, height=LAYER_SIZE)
            return Image.open(io.BytesIO(bytes(png))).convert("RGBA").resize((LAYER_SIZE, LAYER_SIZE))
        # a bitmap/inset wrapper around a single drawable
        source = re.search(ANDROID_NS + r"(?:src|drawable)\(0x[0-9a-f]+\)=(@0x[0-9a-fA-F]{8})", tree)
        return self.layer(source.group(1), depth + 1) if source else None

    def adaptive(self, tree, depth, crop=True):
        def part(name):
            block = re.search(r"E: " + name + r" .*?\n((?:\s+A: .*\n?)*)", tree)
            if block is None:
                return None
            ref = re.search(ANDROID_NS + r"drawable\(0x[0-9a-f]+\)=(@0x[0-9a-fA-F]{8}|#[0-9a-fA-F]+)", block.group(1))
            return self.layer(ref.group(1), depth + 1) if ref else None

        background, foreground = part("background"), part("foreground")
        if background is None or foreground is None:
            return None
        combined = Image.alpha_composite(background, foreground)
        if not crop:
            return combined
        return combined

    def color(self, value, depth=0):
        """A vector color attribute as #AARRGGBB, following resource references; None otherwise."""
        if depth > 5 or not value:
            return None
        if re.fullmatch(r"#[0-9a-fA-F]{6,8}", value):
            return value
        reference = re.fullmatch(r"@(0x[0-9a-fA-F]{8})", value)
        if reference:
            for _config, entry_value in self.table.get(reference.group(1).lower(), []):
                resolved = self.color(entry_value, depth + 1)
                if resolved:
                    return resolved
        return None

    def render(self, icon_path):
        image = self.layer(icon_path)
        if image is None:
            return None
        if icon_path.endswith(".xml"):
            margin = int(LAYER_SIZE * (1 - ADAPTIVE_VISIBLE_FRACTION) / 2)
            image = image.crop((margin, margin, LAYER_SIZE - margin, LAYER_SIZE - margin))
        image = image.resize((ICON_SIZE, ICON_SIZE), Image.LANCZOS)
        out = io.BytesIO()
        image.save(out, format="PNG", optimize=True)
        return out.getvalue()


def _parse_xmltree(text):
    """`aapt2 dump xmltree` output as nested {"name", "attrs", "children"} dicts."""
    root = {"name": None, "attrs": {}, "children": []}
    stack = [(-1, root)]
    for line in text.splitlines():
        indent = len(line) - len(line.lstrip())
        stripped = line.strip()
        element = re.match(r"E: (\S+)", stripped)
        attribute = re.match(r"A: (?:\S+:)?([A-Za-z]+)(?:\(0x[0-9a-f]+\))?=(.*)", stripped)
        if element:
            while stack[-1][0] >= indent:
                stack.pop()
            node = {"name": element.group(1), "attrs": {}, "children": []}
            stack[-1][1]["children"].append(node)
            stack.append((indent, node))
        elif attribute and len(stack) > 1:
            value = attribute.group(2)
            quoted = re.match(r'"(.*?)"(?: \(Raw: .*\))?$', value)
            stack[-1][1]["attrs"][attribute.group(1)] = quoted.group(1) if quoted else value.split(" ")[0]
    return root["children"][0] if root["children"] else None


def _number(value, default=0.0):
    match = re.match(r"-?[0-9.]+", value or "")
    return float(match.group(0)) if match else default


def _svg_paint(argb, alpha):
    """SVG paint + opacity for an #AARRGGBB / #RRGGBB color and an extra alpha multiplier."""
    hex_value = argb[1:].rjust(8, "f") if len(argb) == 7 else argb[1:]
    opacity = int(hex_value[:2], 16) / 255 * alpha
    return f"#{hex_value[2:]}", f"{opacity:.3f}"


def _vector_to_svg(vector, resolve_color):
    """An Android VectorDrawable as SVG markup: paths with solid fills/strokes, groups with their
    transforms and clip-paths. Gradients (which aapt2 inlines as nested resources) are skipped."""
    if vector is None or vector["name"] != "vector":
        return None
    attrs = vector["attrs"]
    width, height = _number(attrs.get("viewportWidth"), 108), _number(attrs.get("viewportHeight"), 108)
    root_alpha = _number(attrs.get("alpha"), 1.0)

    def element(node):
        a = node["attrs"]
        if node["name"] == "path" and a.get("pathData"):
            parts = [f'd="{a["pathData"]}"']
            fill = resolve_color(a.get("fillColor"))
            if fill:
                paint, opacity = _svg_paint(fill, _number(a.get("fillAlpha"), 1.0))
                parts += [f'fill="{paint}"', f'fill-opacity="{opacity}"']
            else:
                parts.append('fill="none"')
            if a.get("fillType") in ("1", "evenOdd"):
                parts.append('fill-rule="evenodd"')
            stroke = resolve_color(a.get("strokeColor"))
            if stroke:
                paint, opacity = _svg_paint(stroke, _number(a.get("strokeAlpha"), 1.0))
                parts += [f'stroke="{paint}"', f'stroke-opacity="{opacity}"', f'stroke-width="{_number(a.get("strokeWidth"))}"']
            return f"<path {' '.join(parts)}/>"
        if node["name"] == "group":
            px, py = _number(a.get("pivotX")), _number(a.get("pivotY"))
            tx, ty = _number(a.get("translateX")), _number(a.get("translateY"))
            transform = (
                f"translate({tx + px} {ty + py}) rotate({_number(a.get('rotation'))}) "
                f"scale({_number(a.get('scaleX'), 1.0)} {_number(a.get('scaleY'), 1.0)}) translate({-px} {-py})"
            )
            return f'<g transform="{transform}">{children(node)}</g>'
        return ""

    clip_counter = [0]

    def children(node):
        # a clip-path clips the siblings that follow it inside the same group
        out, open_groups = [], 0
        for child in node["children"]:
            if child["name"] == "clip-path" and child["attrs"].get("pathData"):
                clip_counter[0] += 1
                clip_id = f"c{clip_counter[0]}"
                out.append(f'<clipPath id="{clip_id}"><path d="{child["attrs"]["pathData"]}"/></clipPath>')
                out.append(f'<g clip-path="url(#{clip_id})">')
                open_groups += 1
            else:
                out.append(element(child))
        return "".join(out) + "</g>" * open_groups

    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{LAYER_SIZE}" height="{LAYER_SIZE}" '
        f'viewBox="0 0 {width} {height}"><g opacity="{root_alpha}">{children(vector)}</g></svg>'
    )


def icon_png(aapt2, apk):
    """PNG bytes of the APK's launcher icon, or None when it can't be rendered."""
    badging = _run(aapt2, "dump", "badging", str(apk))
    match = re.search(r"^application: .*?icon='([^']+)'", badging, re.MULTILINE)
    if match is None:
        return None
    renderer = _Renderer(aapt2, apk)
    try:
        return renderer.render(match.group(1))
    except (KeyError, OSError, subprocess.CalledProcessError, ValueError):
        return None
    finally:
        renderer.zip.close()
