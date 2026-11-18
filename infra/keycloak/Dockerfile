# Keycloak de producción: compilado una vez con el tema de OpsPilot y el realm dentro, arranca
# más rápido (--optimized). Sirve igual en Vercel (contenedor efímero) y en un servidor propio.
FROM quay.io/keycloak/keycloak:26.8.2 AS build
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_FEATURES=persistent-user-sessions \
    KC_CACHE=local
COPY themes/opspilot /opt/keycloak/themes/opspilot
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.2
COPY --from=build /opt/keycloak/ /opt/keycloak/
# Realm de producción. Solo se importa si no existe todavía; un volumen puede reemplazarlo.
COPY realm-prod.json /opt/keycloak/data/import/opspilot-realm.json
# Detrás de un proxy con HTTPS (Vercel o Caddy). Las sesiones de usuario quedan en la base, así que
# sobreviven a que el contenedor se apague; la caché es local porque no hay clúster.
# KC_CACHE también aquí: si solo va en la compilación, al arrancar intenta formar un clúster entre
# instancias que no se ven (Vercel) y tarda minutos esperándolas.
ENV KC_CACHE=local \
    KC_HTTP_ENABLED=true \
    KC_HTTP_PORT=8080 \
    KC_PROXY_HEADERS=xforwarded \
    KC_DB_POOL_MAX_SIZE=8 \
    PORT=8080
EXPOSE 8080
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
