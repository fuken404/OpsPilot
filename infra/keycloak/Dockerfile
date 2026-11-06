# Keycloak de producción: compilado una vez con el tema de OpsPilot dentro, arranca más rápido (--optimized).
FROM quay.io/keycloak/keycloak:26.8.2 AS build
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_FEATURES=persistent-user-sessions
COPY themes/opspilot /opt/keycloak/themes/opspilot
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.2
COPY --from=build /opt/keycloak/ /opt/keycloak/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
