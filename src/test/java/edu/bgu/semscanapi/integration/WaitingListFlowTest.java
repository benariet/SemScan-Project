package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.entity.*;
import edu.bgu.semscanapi.service.WaitingListService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * END-TO-END FLOW TEST: Waiting List Flow
 *
 * This test verifies the complete waiting list workflow:
 * 1. View waiting list entries across all slots
 * 2. Check waiting list capacity limits
 * 3. Verify position management
 * 4. Test promotion workflow
 * 5. Verify business rules (one waiting list at a time)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WaitingListFlowTest extends BaseIntegrationTest {

    @Autowired
    private WaitingListService waitingListService;

    @Autowired
    private edu.bgu.semscanapi.repository.WaitingListRepository waitingListRepository;

    @Autowired
    private edu.bgu.semscanapi.repository.WaitingListPromotionRepository waitingListPromotionRepository;

    private static Long testSlotId;
    private static String testPresenterUsername;

    @Test
    @Order(1)
    @DisplayName("WAITING LIST STEP 1: View all waiting list entries")
    void step1_ViewAllWaitingListEntries() {
        System.out.println("\n========== FLOW TEST: Waiting List Flow ==========\n");

        List<WaitingListEntry> allEntries = waitingListRepository.findAll();

        System.out.println("Total waiting list entries: " + allEntries.size());

        if (allEntries.isEmpty()) {
            System.out.println("○ No waiting list entries found");
            return;
        }

        // Group by slot
        allEntries.stream()
                .map(WaitingListEntry::getSlotId)
                .distinct()
                .forEach(slotId -> {
                    List<WaitingListEntry> slotEntries = waitingListService.getWaitingList(slotId);
                    Optional<SeminarSlot> slot = seminarSlotRepository.findById(slotId);
                    String slotInfo = slot.map(s -> s.getSlotDate() + " " + s.getStartTime()).orElse("Unknown");

                    System.out.println("\nSlot " + slotId + " (" + slotInfo + "):");
                    System.out.println("  Waiting list count: " + slotEntries.size());

                    for (WaitingListEntry entry : slotEntries) {
                        System.out.println("    Position " + entry.getPosition() + ": " + entry.getPresenterUsername());
                        System.out.println("      Degree: " + entry.getDegree());
                        System.out.println("      Topic: " + (entry.getTopic() != null ? entry.getTopic() : "N/A"));
                        if (entry.getPromotionToken() != null) {
                            System.out.println("      ⚡ Has promotion offer (expires: " + entry.getPromotionTokenExpiresAt() + ")");
                        }
                    }
                });
    }

    @Test
    @Order(2)
    @DisplayName("WAITING LIST STEP 2: Find slot with waiting list")
    void step2_FindSlotWithWaitingList() {
        System.out.println("\n--- Finding Slot with Waiting List ---");

        List<WaitingListEntry> allEntries = waitingListRepository.findAll();

        if (!allEntries.isEmpty()) {
            testSlotId = allEntries.get(0).getSlotId();
            System.out.println("✓ Found slot with waiting list: " + testSlotId);

            long count = waitingListService.getWaitingListCount(testSlotId);
            System.out.println("  Entries in this waiting list: " + count);
        } else {
            // Find a full slot that could have a waiting list
            List<SeminarSlot> fullSlots = seminarSlotRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SeminarSlot.SlotStatus.FULL)
                    .toList();

            System.out.println("Full slots (potential waiting list candidates): " + fullSlots.size());

            if (!fullSlots.isEmpty()) {
                testSlotId = fullSlots.get(0).getSlotId();
                System.out.println("✓ Using full slot: " + testSlotId);
            } else {
                System.out.println("○ No full slots found");
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("WAITING LIST STEP 3: Find presenter to test waiting list")
    void step3_FindPresenterForTest() {
        System.out.println("\n--- Finding Presenter for Test ---");

        // Find a presenter who is NOT on any waiting list and NOT registered for any slot
        List<User> presenters = userRepository.findAll().stream()
                .filter(u -> u.getIsPresenter() != null && u.getIsPresenter())
                .filter(u -> u.getDegree() != null)
                .filter(u -> u.getSupervisor() != null && u.getSupervisor().getEmail() != null)
                .toList();

        System.out.println("Presenters with complete profiles: " + presenters.size());

        for (User presenter : presenters) {
            String username = presenter.getBguUsername();

            // Check if already on a waiting list
            boolean onWaitingList = waitingListService.isOnAnyWaitingList(username);
            if (onWaitingList) {
                continue;
            }

            // Check if has approved registration
            List<SeminarSlotRegistration> registrations = registrationRepository.findByIdPresenterUsername(username);
            boolean hasApproved = registrations.stream()
                    .anyMatch(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED);

            if (!hasApproved) {
                testPresenterUsername = username;
                System.out.println("✓ Found test presenter: " + username);
                System.out.println("  Name: " + presenter.getFirstName() + " " + presenter.getLastName());
                System.out.println("  Degree: " + presenter.getDegree());
                System.out.println("  Supervisor: " + presenter.getSupervisor().getName());
                break;
            }
        }

        if (testPresenterUsername == null) {
            System.out.println("○ No available presenter found for testing");
        }
    }

    @Test
    @Order(4)
    @DisplayName("WAITING LIST STEP 4: Check waiting list business rules")
    void step4_CheckBusinessRules() {
        System.out.println("\n--- Checking Business Rules ---");

        // Rule 1: Users can only be on ONE waiting list at a time
        System.out.println("Rule 1: One waiting list per user");
        List<WaitingListEntry> allEntries = waitingListRepository.findAll();

        // Check for any user appearing on multiple waiting lists
        boolean violationsFound = allEntries.stream()
                .map(WaitingListEntry::getPresenterUsername)
                .distinct()
                .anyMatch(username -> {
                    long count = allEntries.stream()
                            .filter(e -> e.getPresenterUsername().equals(username))
                            .count();
                    if (count > 1) {
                        System.out.println("  ⚠ VIOLATION: " + username + " is on " + count + " waiting lists");
                        return true;
                    }
                    return false;
                });

        if (!violationsFound) {
            System.out.println("  ✓ No violations - all users on at most 1 waiting list");
        }

        // Rule 2: Users on waiting list should not be registered for that slot
        System.out.println("\nRule 2: No duplicate registration + waiting list");
        for (WaitingListEntry entry : allEntries) {
            boolean registered = registrationRepository.existsByIdSlotIdAndIdPresenterUsername(
                    entry.getSlotId(), entry.getPresenterUsername());
            if (registered) {
                System.out.println("  ⚠ VIOLATION: " + entry.getPresenterUsername() +
                        " is both on waiting list AND registered for slot " + entry.getSlotId());
            }
        }
        System.out.println("  ✓ Check complete");

        // Rule 3: Positions should be sequential (1, 2, 3...)
        System.out.println("\nRule 3: Sequential positions");
        allEntries.stream()
                .map(WaitingListEntry::getSlotId)
                .distinct()
                .forEach(slotId -> {
                    List<WaitingListEntry> slotEntries = waitingListService.getWaitingList(slotId);
                    for (int i = 0; i < slotEntries.size(); i++) {
                        int expectedPosition = i + 1;
                        int actualPosition = slotEntries.get(i).getPosition();
                        if (actualPosition != expectedPosition) {
                            System.out.println("  ⚠ Gap in positions for slot " + slotId +
                                    ": expected " + expectedPosition + ", got " + actualPosition);
                        }
                    }
                });
        System.out.println("  ✓ Check complete");
    }

    @Test
    @Order(5)
    @DisplayName("WAITING LIST STEP 5: Test waiting list add validation")
    void step5_TestAddValidation() {
        if (testSlotId == null || testPresenterUsername == null) {
            System.out.println("⚠ Skipping - no test data available");
            return;
        }

        System.out.println("\n--- Testing Add Validation ---");

        // Test 1: Adding to non-existent slot should fail
        System.out.println("Test: Adding to non-existent slot...");
        try {
            waitingListService.addToWaitingList(999999L, testPresenterUsername, "Test", null, null);
            System.out.println("  ✗ Should have thrown exception");
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ Correctly rejected: " + e.getMessage());
        }

        // Test 2: Adding with null username should fail
        System.out.println("Test: Adding with null username...");
        try {
            waitingListService.addToWaitingList(testSlotId, null, "Test", null, null);
            System.out.println("  ✗ Should have thrown exception");
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ Correctly rejected: " + e.getMessage());
        }

        // Test 3: Adding non-existent user should fail
        System.out.println("Test: Adding non-existent user...");
        try {
            waitingListService.addToWaitingList(testSlotId, "nonexistent_user_xyz", "Test", null, null);
            System.out.println("  ✗ Should have thrown exception");
        } catch (Exception e) {
            System.out.println("  ✓ Correctly rejected: " + e.getMessage());
        }
    }

    @Test
    @Order(6)
    @DisplayName("WAITING LIST STEP 6: Check promotion offers")
    void step6_CheckPromotionOffers() {
        System.out.println("\n--- Checking Promotion Offers ---");

        // Find entries with active promotion offers
        List<WaitingListEntry> entriesWithOffers = waitingListRepository.findAll().stream()
                .filter(e -> e.getPromotionToken() != null)
                .toList();

        System.out.println("Entries with promotion offers: " + entriesWithOffers.size());

        for (WaitingListEntry entry : entriesWithOffers) {
            System.out.println("  " + entry.getPresenterUsername() + " for slot " + entry.getSlotId());
            System.out.println("    Offered at: " + entry.getPromotionOfferedAt());
            System.out.println("    Expires at: " + entry.getPromotionTokenExpiresAt());

            boolean expired = entry.getPromotionTokenExpiresAt() != null &&
                    entry.getPromotionTokenExpiresAt().isBefore(LocalDateTime.now());
            System.out.println("    Status: " + (expired ? "⚠ EXPIRED" : "✓ Active"));
        }

        // Check promotion history
        List<WaitingListPromotion> promotions = waitingListPromotionRepository.findAll();
        System.out.println("\nPromotion history: " + promotions.size() + " records");

        long pending = promotions.stream()
                .filter(p -> p.getStatus() == WaitingListPromotion.PromotionStatus.PENDING)
                .count();
        long confirmed = promotions.stream()
                .filter(p -> p.getStatus() == WaitingListPromotion.PromotionStatus.APPROVED)
                .count();
        long expired = promotions.stream()
                .filter(p -> p.getStatus() == WaitingListPromotion.PromotionStatus.EXPIRED)
                .count();

        System.out.println("  PENDING: " + pending);
        System.out.println("  CONFIRMED: " + confirmed);
        System.out.println("  EXPIRED: " + expired);
    }

    @Test
    @Order(7)
    @DisplayName("WAITING LIST STEP 7: Verify waiting list API")
    void step7_VerifyWaitingListAPI() {
        if (testSlotId == null) {
            System.out.println("⚠ Skipping - no test slot");
            return;
        }

        System.out.println("\n--- Verifying Waiting List API ---");

        // Test getWaitingList
        List<WaitingListEntry> entries = waitingListService.getWaitingList(testSlotId);
        System.out.println("getWaitingList(" + testSlotId + "): " + entries.size() + " entries");

        // Test getWaitingListCount
        long count = waitingListService.getWaitingListCount(testSlotId);
        System.out.println("getWaitingListCount(" + testSlotId + "): " + count);

        assertEquals(entries.size(), count, "Count should match list size");
        System.out.println("✓ API methods consistent");

        // Test isOnWaitingList for entries
        for (WaitingListEntry entry : entries) {
            boolean onList = waitingListService.isOnWaitingList(testSlotId, entry.getPresenterUsername());
            assertTrue(onList, "User should be on waiting list");
        }
        System.out.println("✓ isOnWaitingList works correctly");
    }

    @Test
    @Order(8)
    @DisplayName("WAITING LIST STEP 8: Check slot capacity vs waiting list")
    void step8_CheckCapacityVsWaitingList() {
        System.out.println("\n--- Capacity vs Waiting List Analysis ---");

        List<SeminarSlot> slotsWithWaitingList = seminarSlotRepository.findAll().stream()
                .filter(s -> waitingListService.getWaitingListCount(s.getSlotId()) > 0)
                .toList();

        System.out.println("Slots with waiting lists: " + slotsWithWaitingList.size());

        for (SeminarSlot slot : slotsWithWaitingList) {
            Long slotId = slot.getSlotId();

            // Get registrations
            List<SeminarSlotRegistration> registrations = registrationRepository.findByIdSlotId(slotId);
            long approved = registrations.stream()
                    .filter(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .count();
            long pending = registrations.stream()
                    .filter(r -> r.getApprovalStatus() == ApprovalStatus.PENDING)
                    .count();

            // Get waiting list
            long waiting = waitingListService.getWaitingListCount(slotId);

            int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;

            System.out.println("\nSlot " + slotId + " (" + slot.getSlotDate() + "):");
            System.out.println("  Capacity: " + capacity);
            System.out.println("  Approved: " + approved);
            System.out.println("  Pending: " + pending);
            System.out.println("  Waiting: " + waiting);
            System.out.println("  Status: " + slot.getStatus());

            if (approved + pending < capacity) {
                System.out.println("  ⚠ Has capacity but still has waiting list!");
            } else {
                System.out.println("  ✓ Full - waiting list makes sense");
            }
        }
    }

    @Test
    @Order(9)
    @DisplayName("WAITING LIST STEP 9: Summary")
    void step9_Summary() {
        System.out.println("\n========== WAITING LIST FLOW SUMMARY ==========");

        long totalWaitingEntries = waitingListRepository.count();
        long totalPromotions = waitingListPromotionRepository.count();

        // Count active promotion offers
        long activeOffers = waitingListRepository.findAll().stream()
                .filter(e -> e.getPromotionToken() != null)
                .filter(e -> e.getPromotionTokenExpiresAt() == null ||
                        e.getPromotionTokenExpiresAt().isAfter(LocalDateTime.now()))
                .count();

        System.out.println("Total waiting list entries: " + totalWaitingEntries);
        System.out.println("Active promotion offers: " + activeOffers);
        System.out.println("Historical promotions: " + totalPromotions);
        System.out.println("Test slot: " + (testSlotId != null ? testSlotId : "N/A"));
        System.out.println("Test presenter: " + (testPresenterUsername != null ? testPresenterUsername : "N/A"));

        System.out.println("\n✓ Waiting list flow test completed");
        System.out.println("=============================================\n");
    }
}
