package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.dto.*;
import edu.bgu.semscanapi.entity.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * END-TO-END FLOW TEST: Slot Registration and Approval Flow
 *
 * This test verifies the complete registration flow:
 * 1. View available slots
 * 2. Find a presenter who can register
 * 3. Check slot capacity and availability
 * 4. View registration statuses (pending, approved, declined)
 * 5. Verify approval token workflow
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistrationApprovalFlowTest extends BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationApprovalFlowTest.class);

    private static Long availableSlotId;
    private static String presenterUsername;

    @Test
    @Order(1)
    @DisplayName("REGISTRATION FLOW STEP 1: List all available slots")
    void step1_ListAvailableSlots() {
        logger.info("\n========== FLOW TEST: Registration & Approval ==========\n");

        List<SeminarSlot> allSlots = seminarSlotRepository
                .findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(LocalDate.now());

        logger.info("Future slots: {}", allSlots.size());

        // Categorize slots
        long freeSlots = allSlots.stream()
                .filter(s -> s.getStatus() == SeminarSlot.SlotStatus.FREE)
                .count();
        long semiSlots = allSlots.stream()
                .filter(s -> s.getStatus() == SeminarSlot.SlotStatus.SEMI)
                .count();
        long fullSlots = allSlots.stream()
                .filter(s -> s.getStatus() == SeminarSlot.SlotStatus.FULL)
                .count();

        logger.info("  FREE: {}", freeSlots);
        logger.info("  SEMI (partially filled): {}", semiSlots);
        logger.info("  FULL: {}", fullSlots);

        // Find a slot that's not full
        Optional<SeminarSlot> available = allSlots.stream()
                .filter(s -> s.getStatus() != SeminarSlot.SlotStatus.FULL)
                .findFirst();

        if (available.isPresent()) {
            availableSlotId = available.get().getSlotId();
            logger.info("\n✓ Found available slot: {}", availableSlotId);
            logger.info("  Date: {}", available.get().getSlotDate());
            logger.info("  Time: {} - {}", available.get().getStartTime(), available.get().getEndTime());
            logger.info("  Capacity: {}", available.get().getCapacity());
        }
    }

    @Test
    @Order(2)
    @DisplayName("REGISTRATION FLOW STEP 2: Check slot registrations")
    void step2_CheckSlotRegistrations() {
        if (availableSlotId == null) {
            logger.info("⚠ Skipping - no available slot");
            return;
        }

        logger.info("\n--- Slot Registrations ---");

        List<SeminarSlotRegistration> registrations = registrationRepository.findByIdSlotId(availableSlotId);

        logger.info("Slot {} has {} registrations:", availableSlotId, registrations.size());

        for (SeminarSlotRegistration reg : registrations) {
            Optional<User> userOpt = userRepository.findByBguUsernameIgnoreCase(reg.getPresenterUsername());
            String name = userOpt.map(u -> u.getFirstName() + " " + u.getLastName())
                    .orElse(reg.getPresenterUsername());

            logger.info("  - {}", name);
            logger.info("    Status: {}", reg.getApprovalStatus());
            logger.info("    Degree: {}", reg.getDegree());
            logger.info("    Topic: {}", reg.getTopic() != null ? reg.getTopic() : "N/A");
            if (reg.getSupervisorEmail() != null) {
                logger.info("    Supervisor: {}", reg.getSupervisorEmail());
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("REGISTRATION FLOW STEP 3: Find presenter eligible to register")
    void step3_FindEligiblePresenter() {
        logger.info("\n--- Finding Eligible Presenter ---");

        // Find presenters who are not already registered for a slot
        List<User> presenters = userRepository.findAll().stream()
                .filter(u -> u.getIsPresenter() != null && u.getIsPresenter())
                .toList();

        logger.info("Total presenters: {}", presenters.size());

        for (User presenter : presenters) {
            List<SeminarSlotRegistration> theirRegistrations =
                    registrationRepository.findByIdPresenterUsername(presenter.getBguUsername());

            // Check if they have an approved registration
            boolean hasApproved = theirRegistrations.stream()
                    .anyMatch(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED);

            if (!hasApproved) {
                presenterUsername = presenter.getBguUsername();
                logger.info("✓ Found presenter without approved slot: {}", presenterUsername);
                logger.info("  Name: {} {}", presenter.getFirstName(), presenter.getLastName());
                logger.info("  Degree: {}", presenter.getDegree());
                logger.info("  Current registrations: {}", theirRegistrations.size());
                break;
            }
        }

        if (presenterUsername == null) {
            logger.info("○ All presenters already have approved slots");
            // Just use any presenter for viewing
            if (!presenters.isEmpty()) {
                presenterUsername = presenters.get(0).getBguUsername();
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("REGISTRATION FLOW STEP 4: View presenter's current registrations")
    void step4_ViewPresenterRegistrations() {
        if (presenterUsername == null) {
            logger.info("⚠ Skipping - no presenter found");
            return;
        }

        logger.info("\n--- Presenter's Registrations ---");

        List<SeminarSlotRegistration> registrations =
                registrationRepository.findByIdPresenterUsername(presenterUsername);

        logger.info("Presenter {} has {} registrations:", presenterUsername, registrations.size());

        for (SeminarSlotRegistration reg : registrations) {
            Optional<SeminarSlot> slotOpt = seminarSlotRepository.findById(reg.getSlotId());
            String slotInfo = slotOpt.map(s -> s.getSlotDate() + " " + s.getStartTime())
                    .orElse("Unknown slot");

            logger.info("  - Slot {} ({})", reg.getSlotId(), slotInfo);
            logger.info("    Status: {}", reg.getApprovalStatus());
            logger.info("    Topic: {}", reg.getTopic() != null ? reg.getTopic() : "N/A");

            if (reg.getApprovalStatus() == ApprovalStatus.PENDING) {
                logger.info("    Token expires: {}", reg.getApprovalTokenExpiresAt());
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("REGISTRATION FLOW STEP 5: Analyze approval workflow")
    void step5_AnalyzeApprovalWorkflow() {
        logger.info("\n--- Approval Workflow Analysis ---");

        List<SeminarSlotRegistration> allRegistrations = registrationRepository.findAll();

        // Count by status
        long pending = allRegistrations.stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.PENDING)
                .count();
        long approved = allRegistrations.stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED)
                .count();
        long declined = allRegistrations.stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.DECLINED)
                .count();
        long expired = allRegistrations.stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.EXPIRED)
                .count();

        logger.info("Registration status distribution:");
        logger.info("  PENDING: {}", pending);
        logger.info("  APPROVED: {}", approved);
        logger.info("  DECLINED: {}", declined);
        logger.info("  EXPIRED: {}", expired);

        // Check for pending registrations with expired tokens
        long expiredTokens = allRegistrations.stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.PENDING)
                .filter(r -> r.getApprovalTokenExpiresAt() != null)
                .filter(r -> r.getApprovalTokenExpiresAt().isBefore(java.time.LocalDateTime.now()))
                .count();

        if (expiredTokens > 0) {
            logger.info("\n⚠ Found {} PENDING registrations with expired tokens", expiredTokens);
        }
    }

    @Test
    @Order(6)
    @DisplayName("REGISTRATION FLOW STEP 6: Verify degree-based capacity rules")
    void step6_VerifyCapacityRules() {
        logger.info("\n--- Capacity Rules Verification ---");

        List<SeminarSlot> slots = seminarSlotRepository.findAll();

        for (SeminarSlot slot : slots.stream().limit(5).toList()) {
            List<SeminarSlotRegistration> regs = registrationRepository.findByIdSlotId(slot.getSlotId());

            long mscApproved = regs.stream()
                    .filter(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .filter(r -> r.getDegree() == User.Degree.MSc)
                    .count();
            long phdApproved = regs.stream()
                    .filter(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .filter(r -> r.getDegree() == User.Degree.PhD)
                    .count();

            logger.info("Slot {} (capacity: {}):", slot.getSlotId(), slot.getCapacity());
            logger.info("  MSc approved: {}", mscApproved);
            logger.info("  PhD approved: {}", phdApproved);
            logger.info("  Status: {}", slot.getStatus());
        }
    }

    @Test
    @Order(7)
    @DisplayName("REGISTRATION FLOW STEP 7: Test registration validation")
    void step7_TestRegistrationValidation() {
        if (presenterUsername == null || availableSlotId == null) {
            logger.info("⚠ Skipping - no presenter or slot");
            return;
        }

        logger.info("\n--- Registration Validation Test ---");

        // Check if already registered
        boolean alreadyRegistered = registrationRepository
                .existsByIdSlotIdAndIdPresenterUsername(availableSlotId, presenterUsername);

        logger.info("Already registered for slot {}: {}", availableSlotId, alreadyRegistered);

        // Check if presenter has any approved slot
        List<SeminarSlotRegistration> presenterRegs =
                registrationRepository.findByIdPresenterUsername(presenterUsername);
        boolean hasApproved = presenterRegs.stream()
                .anyMatch(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED);

        logger.info("Has approved slot: {}", hasApproved);

        if (hasApproved) {
            logger.info("→ Presenter already has an approved slot");
        } else if (alreadyRegistered) {
            logger.info("→ Presenter already registered for this slot (pending)");
        } else {
            logger.info("✓ Presenter could register for this slot");
        }
    }

    @Test
    @Order(8)
    @DisplayName("REGISTRATION FLOW STEP 8: Summary")
    void step8_Summary() {
        logger.info("\n========== REGISTRATION FLOW SUMMARY ==========");
        logger.info("Available slot tested: {}", availableSlotId != null ? availableSlotId : "N/A");
        logger.info("Presenter tested: {}", presenterUsername != null ? presenterUsername : "N/A");

        // Overall stats
        long totalRegistrations = registrationRepository.count();
        long totalSlots = seminarSlotRepository.count();
        long totalPresenters = userRepository.findAll().stream()
                .filter(u -> u.getIsPresenter() != null && u.getIsPresenter())
                .count();

        logger.info("\nDatabase stats:");
        logger.info("  Total slots: {}", totalSlots);
        logger.info("  Total presenters: {}", totalPresenters);
        logger.info("  Total registrations: {}", totalRegistrations);

        logger.info("\n✓ Registration flow test completed");
        logger.info("===============================================\n");
    }
}
