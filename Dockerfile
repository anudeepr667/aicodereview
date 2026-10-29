FROM maven:3.10.0-eclipse-temurin-25-noble AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-noble

ENV SEMGREP_EXECUTABLE=semgrep \
    PATH="/opt/semgrep-venv/bin:${PATH}" \
    HOME=/home/app \
    XDG_CACHE_HOME=/home/app/.cache \
    TMPDIR=/tmp \
    PORT=10000

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        python3 \
        python3-venv \
    && python3 --version \
    && python3 -m venv /opt/semgrep-venv \
    && /opt/semgrep-venv/bin/pip install --no-cache-dir semgrep \
    && /opt/semgrep-venv/bin/semgrep --version \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /home/app --create-home app \
    && mkdir -p /app /home/app/.cache/semgrep \
    && chown -R app:app /app /home/app \
    && chmod 1777 /tmp \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build --chown=10001:10001 \
    /workspace/target/aicodereview-0.0.1-SNAPSHOT.jar \
    /app/app.jar

USER 10001:10001

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "exec java -Djava.io.tmpdir=/tmp -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
