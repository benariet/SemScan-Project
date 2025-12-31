package edu.bgu.semscanapi.unit;

import edu.bgu.semscanapi.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for slot capacity calculation logic.
 *
 * Capacity rules:
 * - PhD students count as 2 toward capacity (configurable, default 2)
 * - MSc students count as 1 toward capacity
 * - Slot capacity is typically 2 (can fit 2 MSc or 1 PhD)
 */
class CapacityCalculationTest {

    private static final int PHD_WEIGHT = 2;
    private static final int MSC_WEIGHT = 1;
    private static final int DEFAULT_SLOT_CAPACITY = 2;

    /**
     * Calculate effective capacity usage.
     * Mirrors PresenterHomeService.calculateEffectiveCapacityUsage()
     */
    private int calculateEffectiveCapacityUsage(List<User.Degree> registrations) {
        int effectiveUsage = 0;
        for (User.Degree degree : registrations) {
            if (degree == User.Degree.PhD) {
                effectiveUsage += PHD_WEIGHT;
            } else {
                effectiveUsage += MSC_WEIGHT;
            }
        }
        return effectiveUsage;
    }

    /**
     * Check if slot is full based on capacity and usage.
     */
    private boolean isSlotFull(int capacity, int effectiveUsage) {
        return effectiveUsage >= capacity;
    }

    /**
     * Calculate remaining capacity.
     */
    private int getAvailableCapacity(int capacity, int effectiveUsage) {
        return Math.max(capacity - effectiveUsage, 0);
    }

    @Nested
    @DisplayName("Degree Weight Tests")
    class DegreeWeightTests {

        @Test
        @DisplayName("PhD counts as 2")
        void phdCountsAsTwo() {
            List<User.Degree> registrations = List.of(User.Degree.PhD);
            assertEquals(2, calculateEffectiveCapacityUsage(registrations));
        }

        @Test
        @DisplayName("MSc counts as 1")
        void mscCountsAsOne() {
            List<User.Degree> registrations = List.of(User.Degree.MSc);
            assertEquals(1, calculateEffectiveCapacityUsage(registrations));
        }

        @Test
        @DisplayName("Empty registrations = 0 usage")
        void emptyRegistrationsZeroUsage() {
            List<User.Degree> registrations = new ArrayList<>();
            assertEquals(0, calculateEffectiveCapacityUsage(registrations));
        }
    }

    @Nested
    @DisplayName("Slot Capacity Tests")
    class SlotCapacityTests {

        @Test
        @DisplayName("One PhD fills slot of capacity 2")
        void onePhdFillsSlot() {
            List<User.Degree> registrations = List.of(User.Degree.PhD);
            int usage = calculateEffectiveCapacityUsage(registrations);

            assertTrue(isSlotFull(DEFAULT_SLOT_CAPACITY, usage));
            assertEquals(0, getAvailableCapacity(DEFAULT_SLOT_CAPACITY, usage));
        }

        @Test
        @DisplayName("Two MSc fill slot of capacity 2")
        void twoMscFillSlot() {
            List<User.Degree> registrations = List.of(User.Degree.MSc, User.Degree.MSc);
            int usage = calculateEffectiveCapacityUsage(registrations);

            assertTrue(isSlotFull(DEFAULT_SLOT_CAPACITY, usage));
            assertEquals(0, getAvailableCapacity(DEFAULT_SLOT_CAPACITY, usage));
        }

        @Test
        @DisplayName("One MSc leaves room for one more")
        void oneMscLeavesRoom() {
            List<User.Degree> registrations = List.of(User.Degree.MSc);
            int usage = calculateEffectiveCapacityUsage(registrations);

            assertFalse(isSlotFull(DEFAULT_SLOT_CAPACITY, usage));
            assertEquals(1, getAvailableCapacity(DEFAULT_SLOT_CAPACITY, usage));
        }

        @Test
        @DisplayName("Empty slot has full capacity available")
        void emptySlotFullCapacity() {
            List<User.Degree> registrations = new ArrayList<>();
            int usage = calculateEffectiveCapacityUsage(registrations);

            assertFalse(isSlotFull(DEFAULT_SLOT_CAPACITY, usage));
            assertEquals(2, getAvailableCapacity(DEFAULT_SLOT_CAPACITY, usage));
        }
    }

    @Nested
    @DisplayName("Mixed Registration Tests")
    class MixedRegistrationTests {

        @Test
        @DisplayName("PhD + MSc exceeds capacity 2")
        void phdPlusMscExceedsCapacity() {
            List<User.Degree> registrations = List.of(User.Degree.PhD, User.Degree.MSc);
            int usage = calculateEffectiveCapacityUsage(registrations);

            assertEquals(3, usage);
            assertTrue(isSlotFull(DEFAULT_SLOT_CAPACITY, usage));
        }

        @Test
        @DisplayName("Two PhD = 4 usage")
        void twoPhdHighUsage() {
            List<User.Degree> registrations = List.of(User.Degree.PhD, User.Degree.PhD);
            int usage = calculateEffectiveCapacityUsage(registrations);

            assertEquals(4, usage);
        }

        @Test
        @DisplayName("Three MSc = 3 usage")
        void threeMscUsage() {
            List<User.Degree> registrations = List.of(
                User.Degree.MSc,
                User.Degree.MSc,
                User.Degree.MSc
            );
            int usage = calculateEffectiveCapacityUsage(registrations);

            assertEquals(3, usage);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Capacity 0 is always full")
        void zeroCapacityAlwaysFull() {
            assertTrue(isSlotFull(0, 0));
        }

        @Test
        @DisplayName("High capacity slot can fit many")
        void highCapacitySlot() {
            int capacity = 10;
            List<User.Degree> registrations = List.of(
                User.Degree.PhD,  // 2
                User.Degree.PhD,  // 2
                User.Degree.MSc,  // 1
                User.Degree.MSc   // 1
            );
            int usage = calculateEffectiveCapacityUsage(registrations);

            assertEquals(6, usage);
            assertFalse(isSlotFull(capacity, usage));
            assertEquals(4, getAvailableCapacity(capacity, usage));
        }

        @Test
        @DisplayName("Available capacity never goes negative")
        void availableNeverNegative() {
            List<User.Degree> registrations = List.of(
                User.Degree.PhD,
                User.Degree.PhD
            );
            int usage = calculateEffectiveCapacityUsage(registrations);

            // Usage is 4, capacity is 2, but available should be 0 not -2
            assertEquals(0, getAvailableCapacity(DEFAULT_SLOT_CAPACITY, usage));
        }
    }

    @ParameterizedTest
    @DisplayName("Capacity scenarios")
    @CsvSource({
        "2, 0, 0, false, 2",   // Empty slot
        "2, 1, 0, false, 1",   // One MSc
        "2, 2, 0, true, 0",    // Two MSc (full)
        "2, 0, 1, true, 0",    // One PhD (full)
        "2, 1, 1, true, 0",    // One MSc + One PhD (over)
        "3, 0, 1, false, 1",   // One PhD in cap-3 slot
        "3, 1, 1, true, 0",    // One MSc + One PhD in cap-3 (full)
    })
    void capacityScenarios(int capacity, int mscCount, int phdCount,
                           boolean expectedFull, int expectedAvailable) {
        List<User.Degree> registrations = new ArrayList<>();
        for (int i = 0; i < mscCount; i++) {
            registrations.add(User.Degree.MSc);
        }
        for (int i = 0; i < phdCount; i++) {
            registrations.add(User.Degree.PhD);
        }

        int usage = calculateEffectiveCapacityUsage(registrations);

        assertEquals(expectedFull, isSlotFull(capacity, usage),
            "Slot full check failed for " + mscCount + " MSc + " + phdCount + " PhD");
        assertEquals(expectedAvailable, getAvailableCapacity(capacity, usage),
            "Available capacity check failed");
    }
}
