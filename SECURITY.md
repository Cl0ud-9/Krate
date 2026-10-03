# Security

Krate installs apps on people's phones, so security problems matter more than most bugs. Thank you for reporting them
responsibly.

## Reporting a problem

**Don't open a public issue.** Report it privately instead:

1. Go to the repository's [Security tab](https://github.com/Cl0ud-9/Krate/security).
2. Choose **Report a vulnerability** and describe what you found.

Please include the Krate version, your Android version, the steps to reproduce it, and what an attacker could do with
it. You'll get a reply within a week. Please give a reasonable amount of time for a fix before sharing details
publicly.

## What counts

Especially welcome:

- Ways to make Krate install an app that wasn't checked: a wrong signing key, a changed file, or a catalog that
  wasn't signed by the maintainer.
- Ways to read or misuse the saved GitHub token.
- Ways for another app on the phone to make Krate install, uninstall or open something without the user asking.

Not security problems (please use a normal issue): crashes without a security impact, problems in the apps Krate
installs (report those to their developers), and anything that needs a rooted or already-compromised phone.

## Supported versions

Only the [latest release](https://github.com/Cl0ud-9/Krate/releases/latest) gets fixes. Krate updates itself, so please
check that you're on it first.
