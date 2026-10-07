package com.sreeyukthag.beinterviewprep.urlshortener.repository;

import com.sreeyukthag.beinterviewprep.urlshortener.entity.ShortUrl;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {

    Optional<ShortUrl> findByCode(String code);

    Optional<ShortUrl> findByDedupeKey(String dedupeKey);

    /** Increments in the database so concurrent visits never overwrite each other's count. */
    @Modifying
    @Query("update ShortUrl s set s.visitCount = s.visitCount + 1 where s.code = :code")
    int incrementVisitCount(@Param("code") String code);
}
