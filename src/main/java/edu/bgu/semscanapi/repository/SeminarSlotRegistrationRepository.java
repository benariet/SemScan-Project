package edu.bgu.semscanapi.repository;

import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SeminarSlotRegistrationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SeminarSlotRegistrationRepository extends JpaRepository<SeminarSlotRegistration, SeminarSlotRegistrationId> {

    List<SeminarSlotRegistration> findByIdPresenterUsername(String presenterUsername);

    List<SeminarSlotRegistration> findByIdSlotId(Long slotId);

    List<SeminarSlotRegistration> findByIdSlotIdIn(Collection<Long> slotIds);

    boolean existsByIdPresenterUsername(String presenterUsername);

    boolean existsByIdSlotIdAndIdPresenterUsername(Long slotId, String presenterUsername);

    long countByIdSlotId(Long slotId);
}


