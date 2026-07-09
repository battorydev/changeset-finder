--liquibase formatted sql

--changeset changeset_name:chageset004 context:UAT,PRD
INSERT INTO TABLE A VALUES(1,'A')
INSERT INTO TABLE A VALUES(2,'B')

--changeset changeset_name:chageset002 context:never
INSERT INTO TABLE A VALUES(3,'C')

--changeset changeset_name:chageset006 context:UAT
INSERT INTO TABLE A VALUES(4,'D')
