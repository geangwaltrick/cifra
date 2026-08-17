# Build em duas etapas: a imagem final nao carrega Maven nem codigo-fonte.
FROM eclipse-temurin:25-jdk AS construcao
WORKDIR /construcao

# Dependencias primeiro, em camada separada: mexer no codigo nao invalida o
# cache do download, e o build seguinte leva segundos em vez de minutos.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

# Usuario sem privilegio: se a aplicacao for comprometida, o atacante nao
# chega como root no container.
RUN useradd --system --uid 1001 cifra
USER cifra

COPY --from=construcao /construcao/target/*.jar aplicacao.jar

# MaxRAMPercentage e o que separa "sobe" de "morre sem explicacao" em free
# tier: sem isso a JVM calcula o heap sobre a memoria da maquina inteira, pede
# mais do que o container tem e leva OOMKill silencioso.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar aplicacao.jar"]
