# Sprint 1 Daily Notes

## Day 1 (Wed, Jan 7)
**Focus**: Sprint planning, project setup

**Completed**:
- [x] Sprint planning session (1h)
- [x] Created GitHub Issues for sprint
- [x] Set up project board

**Tomorrow**: Start Spring Boot configuration

---

## Day 2 (Thu, Jan 8)
**Focus**: Backend and frontend setup

**Completed**:
- [x] Spring Boot project initialized
- [x] Added core dependencies to pom.xml
- [ ] Docker Compose for PostgreSQL

**Blockers**: Weird behaviour of Intellij Idea, had to delete and regenerate .idea folder.

**Notes**: Consider using Spring Boot DevTools for faster reload

---

## Day 3 (Fri, Jan 9)
**Focus**: Frontend, redis, postgres and SonarQube setup

**Completed**:
- [x] Angular project initialized
- [x] Dockerfiles and docker-compose files configured
- [x] SonarQube and Logstash configured
- [ ] CI/CD pipeline configured
- [ ] Basic flyway migrations added

**Blockers**: Weird behaviour of docker compose up command, sometimes runs and sometimes receive ***target frontend: failed to receive status: rpc error: code = Unavailable desc = error reading from server: EOF***, needs further investigation.

**Notes**: Reconfigure docker compose or frontend/Dockerfile file

**Tomorrow**: Start actual development
