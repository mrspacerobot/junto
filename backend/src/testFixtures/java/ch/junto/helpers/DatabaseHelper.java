package ch.junto.helpers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

@Component
public class DatabaseHelper {

    @Autowired
    private EntityManager entityManager;

    /**
     * Persists an entity to the database and flushes the context.
     * This is useful in tests to immediately save fixtures generated in memory.
     */
    @Transactional
    public <T> T save(T entity) {
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }
}
