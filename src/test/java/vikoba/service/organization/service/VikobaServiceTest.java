package vikoba.service.organization.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class VikobaServiceTest {

    @Test
    void validateGroupCycleDates_requiresStartAndEndDates() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> VikobaService.validateGroupCycleDates(null, LocalDate.of(2026, 12, 31)));

        assertTrue(exception.getMessage().contains("startDate"));
    }

    @Test
    void validateGroupCycleDates_acceptsCustomCycleSelectedByUser() {
        LocalDate startDate = LocalDate.of(2026, 1, 15);
        LocalDate endDate = startDate.plusMonths(6).plusDays(12);

        assertDoesNotThrow(() -> VikobaService.validateGroupCycleDates(startDate, endDate));
    }

    @Test
    void validateGroupCycleDates_rejectsSameDayRange() {
        LocalDate startDate = LocalDate.of(2026, 8, 22);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> VikobaService.validateGroupCycleDates(startDate, startDate));

        assertTrue(exception.getMessage().contains("after the start date"));
    }
}
