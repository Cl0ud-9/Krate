package dev.cl0ud9.krate.voice

import java.time.LocalDate

// festivals that move with the moon, by the day they fall on each year, and the regions where they're a public
// holiday; the table runs to 2030 and needs topping up before then. Eid hangs on the moon being sighted, so the day
// after counts too
private class Festival(
    val moment: Moment,
    val regions: Set<String>,
    val days: List<LocalDate>,
    val spread: Long = 0,
)

private val SOUTH_ASIA_AND_DIASPORA = setOf("IN", "NP", "LK", "SG", "MY", "MU", "FJ", "TT", "GY", "SR")

private val FESTIVALS =
    listOf(
        Festival(
            Moment.DIWALI,
            SOUTH_ASIA_AND_DIASPORA,
            listOf("2026-11-08", "2027-10-29", "2028-10-17", "2029-11-05", "2030-10-26").map(LocalDate::parse),
        ),
        Festival(
            Moment.HOLI,
            setOf("IN", "NP"),
            listOf("2027-03-22", "2028-03-11", "2029-03-01", "2030-03-20").map(LocalDate::parse),
        ),
        Festival(
            Moment.EID,
            setOf(
                "IN",
                "PK",
                "BD",
                "ID",
                "MY",
                "SG",
                "AE",
                "SA",
                "QA",
                "KW",
                "BH",
                "OM",
                "EG",
                "TR",
                "MA",
                "DZ",
                "TN",
                "JO",
                "IQ",
                "NG",
                "SN",
                "MV",
                "BN",
            ),
            listOf("2027-03-09", "2028-02-26", "2029-02-14", "2030-02-04").map(LocalDate::parse),
            spread = 1,
        ),
        Festival(
            Moment.LUNAR_NEW_YEAR,
            setOf("CN", "TW", "HK", "MO", "SG", "MY", "VN", "KR", "ID"),
            listOf("2027-02-06", "2028-01-26", "2029-02-13", "2030-02-03").map(LocalDate::parse),
        ),
    )

// the festival being celebrated today where the phone is set to, if any
internal fun festivalOn(
    today: LocalDate,
    region: String?,
): Moment? {
    if (region == null) return null
    return FESTIVALS
        .firstOrNull { festival ->
            region in festival.regions &&
                festival.days.any { day -> !today.isBefore(day) && !today.isAfter(day.plusDays(festival.spread)) }
        }?.moment
}
