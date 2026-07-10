--liquibase formatted sql

--changeset changeset_name:chageset001 context:UAT,PRD
INSERT INTO TABLE A VALUES(1,'A')
INSERT INTO TABLE B VALUES(2,'B')

--changeset changeset_name:chageset002 context:never
INSERT INTO TABLE A VALUES(3,'C')

--changeset changeset_name:chageset003 context:UAT
INSERT INTO TABLE C VALUES(4,'D')
