package fi.kiekkopolku.app

import fi.kiekkopolku.app.domain.Course
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CourseCardDataTest {
    private val course = Course("c", "Testirata", null, null, 61.5, 23.7)
    @Test fun mapCoordinatesDoNotInventHumanLocation() {
        assertTrue(course.hasLocation)
        assertNull(course.locationLabel())
        assertNull(course.copy(city="  ", country="").locationLabel())
        assertEquals("Tampere", course.copy(city=" Tampere ").locationLabel())
        assertEquals("Ratakatu 1", course.copy(address="Ratakatu 1").locationLabel())
        assertEquals("Suomi", course.copy(country="FI").locationLabel(Locale.forLanguageTag("fi")))
        assertNull(course.copy(country="ZZ").locationLabel())
    }
    @Test fun explicitInactiveMarkerIsSeparatedWithoutChangingSearchName() {
        val inactive = course.copy(name="Testirata (EI KÄYTÖSSÄ) → Siniset koripaikat")
        assertTrue(inactive.isInactive)
        assertEquals("Testirata → Siniset koripaikat", inactive.displayName)
        assertEquals("Testirata (EI KÄYTÖSSÄ) → Siniset koripaikat", inactive.name)
        assertFalse(course.copy(name="Vanha rata (2020)").isInactive)
        assertEquals("Vanha rata (2020)", course.copy(name="Vanha rata (2020)").displayName)
    }
}
