/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hibernate.bugs;

import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.hibernate.Hibernate;
import org.hibernate.boot.beanvalidation.HibernateTraversableResolver;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.testing.bytecode.enhancement.EnhancementOptions;
import org.hibernate.testing.bytecode.enhancement.extension.BytecodeEnhanced;
import org.hibernate.testing.jdbc.SQLStatementInspector;
import org.hibernate.testing.orm.junit.*;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@DomainModel(annotatedClasses = MyEntity.class)
@ServiceRegistry(
        settings = {
                @Setting(name = AvailableSettings.SHOW_SQL, value = "true"),
                @Setting(name = AvailableSettings.FORMAT_SQL, value = "true"),
                @Setting(name = AvailableSettings.JAKARTA_VALIDATION_MODE, value = "CALLBACK")
        }
)
@SessionFactory(useCollectingStatementInspector = true, applyCollectionsInDefaultFetchGroup = false)
@EnhancementOptions(lazyLoading = true)
@BytecodeEnhanced(runNotEnhancedAsWell = true)
class ORMUnitTestCase {

    @Test
    void withHibernateValidator(SessionFactoryScope scope) {
        scope.inTransaction(session -> session.persist(new MyEntity().setId(1L)));

        SQLStatementInspector inspector = scope.getCollectingStatementInspector();
        inspector.clear();

        scope.inTransaction(session -> {
            MyEntity entity = session.find(MyEntity.class, 1L);
            // This update forces Hibernate Validator to check for @Size on entity.lazyCols
            entity.mutableField++;
        });
        // 1 select, 1 update
        inspector.assertExecutedCount(2);
    }

    @Test
    void hibernateIsPropertyInitialized(SessionFactoryScope scope) {
        scope.inTransaction(session -> session.persist(new MyEntity().setId(2L)));

        scope.inTransaction(session -> {
            MyEntity entity = session.find(MyEntity.class, 2L);
            assertTrue(Hibernate.isInitialized(entity));
            assertFalse(Hibernate.isPropertyInitialized(entity, "lazyCols"));
        });
    }

    @Test
    void testHibernateTraversableResolver(SessionFactoryScope scope) {
        scope.inTransaction(session -> session.persist(new MyEntity().setId(3L)));
        scope.inTransaction(session -> {
            MyEntity entity = session.find(MyEntity.class, 3L);

            // Should we be more aggressive in the traversable resolver implementation?
            HibernateTraversableResolver traversableResolver =
                    new HibernateTraversableResolver(null, irrelevantHack(), scope.getSessionFactory());

            assertFalse(traversableResolver.isReachable(
                    entity, hackOnlyName("lazyCols"),
                    null, null, null));
        });
    }

    private static Path.Node hackOnlyName(String propertyName) {
        return new Path.Node() {

            @Override
            public String getName() {
                return propertyName;
            }

            @Override
            public boolean isInIterable() {
                return false;
            }

            @Override
            public Integer getIndex() {
                return 0;
            }

            @Override
            public Object getKey() {
                return null;
            }

            @Override
            public ElementKind getKind() {
                return null;
            }

            @Override
            public <T extends Path.Node> T as(Class<T> nodeType) {
                return null;
            }
        };
    }

    private static ConcurrentHashMap<EntityPersister, Set<String>> irrelevantHack() {
        return new ConcurrentHashMap<>() {
            @Override
            public Set<String> get(Object key) {
                return Set.of();
            }
        };
    }
}
