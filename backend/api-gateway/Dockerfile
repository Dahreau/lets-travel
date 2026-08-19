FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system spring && adduser --system --ingroup spring spring
# Gateway MVC ignore spring.http.client.ssl.bundle pour son client HTTP interne (verifie
# empiriquement) : on importe donc le certificat directement dans le keystore JDK.
COPY --from=internal-tls-certs internal.crt /tmp/internal.crt
RUN keytool -importcert -noprompt -trustcacerts -alias travel-plan-internal \
    -file /tmp/internal.crt -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
    && rm /tmp/internal.crt
USER spring:spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
