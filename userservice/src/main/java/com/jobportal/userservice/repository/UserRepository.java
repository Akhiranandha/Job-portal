package com.jobportal.userservice.repository;

import com.jobportal.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndIsActiveTrue(String email);

    List<User> findByIsActiveTrue();
}
