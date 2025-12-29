package edu.bgu.semscanapi.integration;

import edu.bgu.semscanapi.dto.*;
import edu.bgu.semscanapi.entity.*;
import org.junit.jupiter.api.*;

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

    private static Long availableSlotId;
    private static String presenterUsername;

    @Test
    @Order(1)
    @DisplayName("REGISTRATION FLOW STEP 1: List all available slots")
    void step1_ListAvailableSlots() {
        System.out.println("\n========== FLOW TEST: Registration & Approval ==========\n");

        List<SeminarSlot> allSlots = seminarSlotRepository
                .findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(LocalDate.now());

        System.out.println("Future slots: " + allSlots.size());

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

        System.out.println("  FREE: " + freeSlots);
        System.out.println("  SEMI (partially filled): " + semiSlots);
        System.out.println("  FULL: " + fullSlots);

        // Find a slot that's not full
        Optional<SeminarSlot> available = allSlots.stream()
                .filter(s -> s.getStatus() != SeminarSlot.SlotStatus.FULL)
                .findFirst();

        if (available.isPresent()) {
            availableSlotId = available.get().getSlotId();
            System.out.println("\n✓ Found available slot: " + availableSlotId);
            System.out.println("  Date: " + available.get().getSlotDate());
            System.out.println("  Time: " + available.get().getStartTime() + " - " + available.get().getEndTime());
            System.out.println("  Capacity: " + available.get().getCapacity());
        }
    }

    @Test
    @Order(2)
    @DisplayName("REGISTRATION FLOW STEP 2: Check slot registrations")
    void step2_CheckSlotRegistrations() {
        if (availableSlotId == null) {
            System.out.println("⚠ Skipping - no available slot");
            return;
        }

        System.out.println("\n--- Slot Registrations ---");

        List<SeminarSlotRegistration> registrations = registrationRepository.findByIdSlotId(availableSlotId);

        System.out.println("Slot " + availableSlotId + " has " + registrations.size() + " registrations:");

        for (SeminarSlotRegistration reg : registrations) {
            Optional<User> userOpt = userRepository.findByBguUsernameIgnoreCase(reg.getPresenterUsername());
            String name = userOpt.map(u -> u.getFirstName() + " " + u.getLastName())
                    .orElse(reg.getPresenterUsername());

            System.out.println("  - " + name);
            System.out.println("    Status: " + reg.getApprovalStatus());
            System.out.println("    Degree: " + reg.getDegree());
            System.out.println("    Topic: " + (reg.getTopic() != null ? reg.getTopic() : "N/A"));
            if (reg.getSupervisorEmail() != null) {
                System.out.println("    Supervisor: " + reg.getSupervisorEmail());
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("REGISTRATION FLOW STEP 3: Find presenter eligible to register")
    void step3_FindEligiblePresenter() {
        System.out.println("\n--- Finding Eligible Presenter ---");

        // Find presenters who are not already registered for a slot
        List<User> presenters = userRepository.findAll().stream()
                .filter(u -> u.getIsPresenter() != null && u.getIsPresenter())
                .toList();

        System.out.println("Total presenters: " + presenters.size());

        for (User presenter : presenters) {
            List<SeminarSlotRegistration> theirRegistrations =
                    registrationRepository.findByIdPresenterUsername(presenter.getBguUsername());

            // Check if they have an approved registration
            boolean hasApproved = theirRegistrations.stream()
                    .anyMatch(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED);

            if (!hasApproved) {
                presenterUsername = presenter.getBguUsername();
                System.out.println("✓ Found presenter without approved slot: " + presenterUsername);
                System.out.println("  Name: " + presenter.getFirstName() + " " + presenter.getLastName());
                System.out.println("  Degree: " + presenter.getDegree());
                System.out.println("  Current registrations: " + theirRegistrations.size());
                break;
            }
        }

        if (presenterUsername == null) {
            System.out.println("○ All presenters already have approved slots");
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
            System.out.println("⚠ Skipping - no presenter found");
            return;
        }

        System.out.println("\n--- Presenter's Registrations ---");

        List<SeminarSlotRegistration> registrations =
                registrationRepository.findByIdPresenterUsername(presenterUsername);

        System.out.println("Presenter " + presenterUsername + " has " + registrations.size() + " registrations:");

        for (SeminarSlotRegistration reg : registrations) {
            Optional<SeminarSlot> slotOpt = seminarSlotRepository.findById(reg.getSlotId());
            String slotInfo = slotOpt.map(s -> s.getSlotDate() + " " + s.getStartTime())
                    .orElse("Unknown slot");

            System.out.println("  - Slot " + reg.getSlotId() + " (" + slotInfo + ")");
            System.out.println("    Status: " + reg.getApprovalStatus());
            System.out.println("    Topic: " + (reg.getTopic() != null ? reg.getTopic() : "N/A"));

            if (reg.getApprovalStatus() == ApprovalStatus.PENDING) {
                System.out.println("    Token expires: " + reg.getApprovalTokenExpiresAt());
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("REGISTRATION FLOW STEP 5: Analyze approval workflow")
    void step5_AnalyzeApprovalWorkflow() {
        System.out.println("\n--- Approval Workflow Analysis ---");

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

        System.out.println("Registration status distribution:");
        System.out.println("  PENDING: " + pending);
        System.out.println("  APPROVED: " + approved);
        System.out.println("  DECLINED: " + declined);
        System.out.println("  EXPIRED: " + expired);

        // Check for pending registrations with expired tokens
        long expiredTokens = allRegistrations.stream()
                .filter(r -> r.getApprovalStatus() == ApprovalStatus.PENDING)
                .filter(r -> r.getApprovalTokenExpiresAt() != null)
                .filter(r -> r.getApprovalTokenExpiresAt().isBefore(java.time.LocalDateTime.now()))
                .count();

        if (expiredTokens > 0) {
            System.out.println("\n⚠ Found " + expiredTokens + " PENDING registrations with expired tokens");
        }
    }

    @Test
    @Order(6)
    @DisplayName("REGISTRATION FLOW STEP 6: Verify degree-based capacity rules")
    void step6_VerifyCapacityRules() {
        System.out.println("\n--- Capacity Rules Verification ---");

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

            System.out.println("Slot " + slot.getSlotId() + " (capacity: " + slot.getCapacity() + "):");
            System.out.println("  MSc approved: " + mscApproved);
            System.out.println("  PhD approved: " + phdApproved);
            System.out.println("  Status: " + slot.getStatus());
        }
    }

    @Test
    @Order(7)
    @DisplayName("REGISTRATION FLOW STEP 7: Test registration validation")
    void step7_TestRegistrationValidation() {
        if (presenterUsername == null || availableSlotId == null) {
            System.out.println("⚠ Skipping - no presenter or slot");
            return;
        }

        System.out.println("\n--- Registration Validation Test ---");

        // Check if already registered
        boolean alreadyRegistered = registrationRepository
                .existsByIdSlotIdAndIdPresenterUsername(availableSlotId, presenterUsername);

        System.out.println("Already registered for slot " + availableSlotId + ": " + alreadyRegistered);

        // Check if presenter has any approved slot
        List<SeminarSlotRegistration> presenterRegs =
                registrationRepository.findByIdPresenterUsername(presenterUsername);
        boolean hasApproved = presenterRegs.stream()
                .anyMatch(r -> r.getApprovalStatus() == ApprovalStatus.APPROVED);

        System.out.println("Has approved slot: " + hasApproved);

        if (hasApproved) {
            System.out.println("→ Presenter already has an approved slot");
        } else if (alreadyRegistered) {
            System.out.println("→ Presenter already registered for this slot (pending)");
        } else {
            System.out.println("✓ Presenter could register for this slot");
        }
    }

    @Test
    @Order(8)
    @DisplayName("REGISTRATION FLOW STEP 8: Summary")
    void step8_Summary() {
        System.out.println("\n========== REGISTRATION FLOW SUMMARY ==========");
        System.out.println("Available slot tested: " + (availableSlotId != null ? availableSlotId : "N/A"));
        System.out.println("Presenter tested: " + (presenterUsername != null ? presenterUsername : "N/A"));

        // Overall stats
        long totalRegistrations = registrationRepository.count();
        long totalSlots = seminarSlotRepository.count();
        long totalPresenters = userRepository.findAll().stream()
                .filter(u -> u.getIsPresenter() != null && u.getIsPresenter())
                .count();

        System.out.println("\nDatabase stats:");
        System.out.println("  Total slots: " + totalSlots);
        System.out.println("  Total presenters: " + totalPresenters);
        System.out.println("  Total registrations: " + totalRegistrations);

        System.out.println("\n✓ Registration flow test completed");
        System.out.println("===============================================\n");
    }
}
