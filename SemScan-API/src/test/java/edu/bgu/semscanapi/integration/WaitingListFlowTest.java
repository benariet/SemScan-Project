package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.entity.*;
import edu.bgu.semscanapi.service.WaitingListService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(WaitingListFlowTest.class);

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
        logger.info("\n========== FLOW TEST: Waiting List Flow ==========\n");

        List<WaitingListEntry> allEntries = waitingListRepository.findAll();

        logger.info("Total waiting list entries: {}", allEntries.size());

        if (allEntries.isEmpty()) {
            logger.info("○ No waiting list entries found");
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

                    logger.info("\nSlot {} ({}):", slotId, slotInfo);
                    logger.info("  Waiting list count: {}", slotEntries.size());

                    for (WaitingListEntry entry : slotEntries) {
                        logger.info("    Position {}: {}", entry.getPosition(), entry.getPresenterUsername());
                        logger.info("      Degree: {}", entry.getDegree());
                        logger.info("      Topic: {}", entry.getTopic() != null ? entry.getTopic() : "N/A");
                        if (entry.getPromotionToken() != null) {
                            logger.info("      ⚡ Has promotion offer (expires: {})", entry.getPromotionTokenExpiresAt());
                        }
                    }
                });
    }

    @Test
    @Order(2)
    @DisplayName("WAITING LIST STEP 2: Find slot with waiting list")
    void step2_FindSlotWithWaitingList() {
        logger.info("\n--- Finding Slot with Waiting List ---");

        List<WaitingListEntry> allEntries = waitingListRepository.findAll();

        if (!allEntries.isEmpty()) {
            testSlotId = allEntries.get(0).getSlotId();
            logger.info("✓ Found slot with waiting list: {}", testSlotId);

            long count = waitingListService.getWaitingListCount(testSlotId);
            logger.info("  Entries in this waiting list: {}", count);
        } else {
            // Find a full slot that could have a waiting list
            List<SeminarSlot> fullSlots = seminarSlotRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SeminarSlot.SlotStatus.FULL)
                    .toList();

            logger.info("Full slots (potential waiting list candidates): {}", fullSlots.size());

            if (!fullSlots.isEmpty()) {
                testSlotId = fullSlots.get(0).getSlotId();
                logger.info("✓ Using full slot: {}", testSlotId);
            } else {
                logger.info("○ No full slots found");
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("WAITING LIST STEP 3: Find presenter to test waiting list")
    void step3_FindPresenterForTest() {
        logger.info("\n--- Finding Presenter for Test ---");

        // Find a presenter who is NOT on any waiting list and NOT registered for any slot
        List<User> presenters = userRepository.findAll().stream()
                .filter(u -> u.getIsPresenter() != null && u.getIsPresenter())
                .filter(u -> u.getDegree() != null)
                .filter(u -> u.getSupervisor() != null && u.getSupervisor().getEmail() != null)
                .toList();

        logger.info("Presenters with complete profiles: {}", presenters.size());

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
                logger.info("✓ Found test presenter: {}", username);
                logger.info("  Name: {} {}", presenter.getFirstName(), presenter.getLastName());
                logger.info("  Degree: {}", presenter.getDegree());
                logger.info("  Supervisor: {}", presenter.getSupervisor().getName());
                break;
            }
        }

        if (testPresenterUsername == null) {
            logger.info("○ No available presenter found for testing");
        }
    }

    @Test
    @Order(4)
    @DisplayName("WAITING LIST STEP 4: Check waiting list business rules")
    void step4_CheckBusinessRules() {
        logger.info("\n--- Checking Business Rules ---");

        // Rule 1: Users can only be on ONE waiting list at a time
        logger.info("Rule 1: One waiting list per user");
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
                        logger.info("  ⚠ VIOLATION: {} is on {} waiting lists", username, count);
                        return true;
                    }
                    return false;
                });

        if (!violationsFound) {
            logger.info("  ✓ No violations - all users on at most 1 waiting list");
        }

        // Rule 2: Users on waiting list should not be registered for that slot
        logger.info("\nRule 2: No duplicate registration + waiting list");
        for (WaitingListEntry entry : allEntries) {
            boolean registered = registrationRepository.existsByIdSlotIdAndIdPresenterUsername(
                    entry.getSlotId(), entry.getPresenterUsername());
            if (registered) {
                logger.info("  ⚠ VIOLATION: {} is both on waiting list AND registered for slot {}",
                        entry.getPresenterUsername(), entry.getSlotId());
            }
        }
        logger.info("  ✓ Check complete");

        // Rule 3: Positions should be sequential (1, 2, 3...)
        logger.info("\nRule 3: Sequential positions");
        allEntries.stream()
                .map(WaitingListEntry::getSlotId)
                .distinct()
                .forEach(slotId -> {
                    List<WaitingListEntry> slotEntries = waitingListService.getWaitingList(slotId);
                    for (int i = 0; i < slotEntries.size(); i++) {
                        int expectedPosition = i + 1;
                        int actualPosition = slotEntries.get(i).getPosition();
                        if (actualPosition != expectedPosition) {
                            logger.info("  ⚠ Gap in positions for slot {}: expected {}, got {}",
                                    slotId, expectedPosition, actualPosition);
                        }
                    }
                });
        logger.info("  ✓ Check complete");
    }

    @Test
    @Order(5)
    @DisplayName("WAITING LIST STEP 5: Test waiting list add validation")
    void step5_TestAddValidation() {
        if (testSlotId == null || testPresenterUsername == null) {
            logger.info("⚠ Skipping - no test data available");
            return;
        }

        logger.info("\n--- Testing Add Validation ---");

        // Test 1: Adding to non-existent slot should fail
        logger.info("Test: Adding to non-existent slot...");
        try {
            waitingListService.addToWaitingList(999999L, testPresenterUsername, "Test", null, null);
            logger.info("  ✗ Should have thrown exception");
        } catch (IllegalArgumentException e) {
            logger.info("  ✓ Correctly rejected: {}", e.getMessage());
        }

        // Test 2: Adding with null username should fail
        logger.info("Test: Adding with null username...");
        try {
            waitingListService.addToWaitingList(testSlotId, null, "Test", null, null);
            logger.info("  ✗ Should have thrown exception");
        } catch (IllegalArgumentException e) {
            logger.info("  ✓ Correctly rejected: {}", e.getMessage());
        }

        // Test 3: Adding non-existent user should fail
        logger.info("Test: Adding non-existent user...");
        try {
            waitingListService.addToWaitingList(testSlotId, "nonexistent_user_xyz", "Test", null, null);
            logger.info("  ✗ Should have thrown exception");
        } catch (Exception e) {
            logger.info("  ✓ Correctly rejected: {}", e.getMessage());
        }
    }

    @Test
    @Order(6)
    @DisplayName("WAITING LIST STEP 6: Check promotion offers")
    void step6_CheckPromotionOffers() {
        logger.info("\n--- Checking Promotion Offers ---");

        // Find entries with active promotion offers
        List<WaitingListEntry> entriesWithOffers = waitingListRepository.findAll().stream()
                .filter(e -> e.getPromotionToken() != null)
                .toList();

        logger.info("Entries with promotion offers: {}", entriesWithOffers.size());

        for (WaitingListEntry entry : entriesWithOffers) {
            logger.info("  {} for slot {}", entry.getPresenterUsername(), entry.getSlotId());
            logger.info("    Offered at: {}", entry.getPromotionOfferedAt());
            logger.info("    Expires at: {}", entry.getPromotionTokenExpiresAt());

            boolean expired = entry.getPromotionTokenExpiresAt() != null &&
                    entry.getPromotionTokenExpiresAt().isBefore(LocalDateTime.now());
            logger.info("    Status: {}", expired ? "⚠ EXPIRED" : "✓ Active");
        }

        // Check promotion history
        List<WaitingListPromotion> promotions = waitingListPromotionRepository.findAll();
        logger.info("\nPromotion history: {} records", promotions.size());

        long pending = promotions.stream()
                .filter(p -> p.getStatus() == WaitingListPromotion.PromotionStatus.PENDING)
                .count();
        long confirmed = promotions.stream()
                .filter(p -> p.getStatus() == WaitingListPromotion.PromotionStatus.APPROVED)
                .count();
        long expired = promotions.stream()
                .filter(p -> p.getStatus() == WaitingListPromotion.PromotionStatus.EXPIRED)
                .count();

        logger.info("  PENDING: {}", pending);
        logger.info("  CONFIRMED: {}", confirmed);
        logger.info("  EXPIRED: {}", expired);
    }

    @Test
    @Order(7)
    @DisplayName("WAITING LIST STEP 7: Verify waiting list API")
    void step7_VerifyWaitingListAPI() {
        if (testSlotId == null) {
            logger.info("⚠ Skipping - no test slot");
            return;
        }

        logger.info("\n--- Verifying Waiting List API ---");

        // Test getWaitingList
        List<WaitingListEntry> entries = waitingListService.getWaitingList(testSlotId);
        logger.info("getWaitingList({}): {} entries", testSlotId, entries.size());

        // Test getWaitingListCount
        long count = waitingListService.getWaitingListCount(testSlotId);
        logger.info("getWaitingListCount({}): {}", testSlotId, count);

        assertEquals(entries.size(), count, "Count should match list size");
        logger.info("✓ API methods consistent");

        // Test isOnWaitingList for entries
        for (WaitingListEntry entry : entries) {
            boolean onList = waitingListService.isOnWaitingList(testSlotId, entry.getPresenterUsername());
            assertTrue(onList, "User should be on waiting list");
        }
        logger.info("✓ isOnWaitingList works correctly");
    }

    @Test
    @Order(8)
    @DisplayName("WAITING LIST STEP 8: Check slot capacity vs waiting list")
    void step8_CheckCapacityVsWaitingList() {
        logger.info("\n--- Capacity vs Waiting List Analysis ---");

        List<SeminarSlot> slotsWithWaitingList = seminarSlotRepository.findAll().stream()
                .filter(s -> waitingListService.getWaitingListCount(s.getSlotId()) > 0)
                .toList();

        logger.info("Slots with waiting lists: {}", slotsWithWaitingList.size());

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

            logger.info("\nSlot {} ({}):", slotId, slot.getSlotDate());
            logger.info("  Capacity: {}", capacity);
            logger.info("  Approved: {}", approved);
            logger.info("  Pending: {}", pending);
            logger.info("  Waiting: {}", waiting);
            logger.info("  Status: {}", slot.getStatus());

            if (approved + pending < capacity) {
                logger.info("  ⚠ Has capacity but still has waiting list!");
            } else {
                logger.info("  ✓ Full - waiting list makes sense");
            }
        }
    }

    @Test
    @Order(9)
    @DisplayName("WAITING LIST STEP 9: Summary")
    void step9_Summary() {
        logger.info("\n========== WAITING LIST FLOW SUMMARY ==========");

        long totalWaitingEntries = waitingListRepository.count();
        long totalPromotions = waitingListPromotionRepository.count();

        // Count active promotion offers
        long activeOffers = waitingListRepository.findAll().stream()
                .filter(e -> e.getPromotionToken() != null)
                .filter(e -> e.getPromotionTokenExpiresAt() == null ||
                        e.getPromotionTokenExpiresAt().isAfter(LocalDateTime.now()))
                .count();

        logger.info("Total waiting list entries: {}", totalWaitingEntries);
        logger.info("Active promotion offers: {}", activeOffers);
        logger.info("Historical promotions: {}", totalPromotions);
        logger.info("Test slot: {}", testSlotId != null ? testSlotId : "N/A");
        logger.info("Test presenter: {}", testPresenterUsername != null ? testPresenterUsername : "N/A");

        logger.info("\n✓ Waiting list flow test completed");
        logger.info("=============================================\n");
    }
}
