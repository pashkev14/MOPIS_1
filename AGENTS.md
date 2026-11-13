# AGENTS.md

## Project state
- `MOPIS_1` is a course assignment repo (Russian README). It is currently a scaffold: `pom.xml` is **empty (0 bytes)** and there is no `src/` yet. Do not assume any build, test, or lint command works until the Maven project is created.
- Intended stack is **Java 25 + Maven** (see `.docker/opencode/Dockerfile`), not Gradle. `.gitignore` already ignores `target/`, `build/`, `.gradle/`, `*.class`, `*.jar`.

## Environment / commands
- Java 25 and Maven 3.9 are available on the host, but the intended dev environment is the Docker Compose service `opencode` (`container_name: opencode-java25`).
- Start/enter it with `docker compose up -d --build` then `docker compose exec opencode bash`; or run one-off commands with `docker compose run --rm opencode <cmd>`.
- The repo is bind-mounted at `/workspace`; the Maven cache persists in the `maven_cache` volume at `/home/coder/.m2`.

## OpenCode provider setup
- Provider config is `.docker/opencode/opencode.json`, mounted read-only to `/home/coder/.config/opencode/opencode.json`. It uses RouterAI and requires `ROUTERAI_API_KEY`.
- Create `.env` from `.env.example` and set the key. `.env` is gitignored and contains a real secret — never commit it or echo its contents.

## Verification
- No CI, tests, linter, or formatter are configured. Once `pom.xml`/`src` exist, run `mvn test` or `mvn verify` from the repo root; a single test is `mvn -Dtest=ClassName#method test`.
- There is no existing code to imitate; establish package layout and conventions before adding substantial code.
