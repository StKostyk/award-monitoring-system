-- R__seed_organizations.sql
-- Description: Seed data for organizations - Idempotent
-- Author: Stefan Kostyk
-- Note: This migration re-runs whenever the file changes

-- ============================================================================
-- SEED ORGANIZATIONS
-- ============================================================================
-- Hierarchical structure: University -> Faculties -> Departments
--                                       Colleges -> Specialities
-- Using UPSERT pattern for idempotency
-- ============================================================================

-- Temporarily disable triggers for bulk insert
ALTER TABLE organizations DISABLE TRIGGER trg_organizations_audit;

-- Clear and reseed (reference data approach)
TRUNCATE TABLE organizations CASCADE;

-- Reset sequence
ALTER SEQUENCE organizations_org_id_seq RESTART WITH 1;

-- ============================================================================
-- UNIVERSITY (Root Level)
-- ============================================================================

INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (1, 'Yuriy Fedkovych Chernivtsi National University', 'Чернівецький національний університет імені Юрія Федьковича', 'ChNU', 'UNIVERSITY', NULL, 0, 'chnu', TRUE),
    (14, 'Applied College of Yuriy Fedkovych Chernivtsi National University', 'Фаховий коледж Чернівецького національного університету імені Юрія Федьковича', 'AC', 'COLLEGE', 1, 1, 'chnu.ac', TRUE);

-- ============================================================================
-- FACULTIES (Level 1)
-- ============================================================================

INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (2, 'Institute of Biology, Chemistry and Bioresources', 'Навчально-науковий інститут біології, хімії та біоресурсів', 'IBCB', 'FACULTY', 1, 1, 'chnu.ibcb', TRUE),
    (3, 'Institute of Physical, Technical and Computer Sciences', 'Навчально-науковий інститут фізико-технічних та комп’ютерних наук', 'IPTCS', 'FACULTY', 1, 1, 'chnu.iptcs', TRUE),
    (4, 'Faculty of Architecture, Construction, Decorative and Applied Arts', 'Факультет архітектури, будівництва та декоративно-прикладного мистецтва', 'FAB', 'FACULTY', 1, 1, 'chnu.fab', TRUE),
    (5, 'Faculty of Geography', 'Географічний факультет', 'FG', 'FACULTY', 1, 1, 'chnu.fg', TRUE),
    (6, 'Faculty of Economics', 'Економічний факультет', 'FE', 'FACULTY', 1, 1, 'chnu.fe', TRUE),
    (7, 'Faculty of Foreign Languages', 'Факультет іноземних мов', 'FFl', 'FACULTY', 1, 1, 'chnu.ffl', TRUE),
    (8, 'Faculty of History, Political Science and International Relations', 'Факультет історії, політології та міжнародних відносин', 'FHPIR', 'FACULTY', 1, 1, 'chnu.fhpir', TRUE),
    (9, 'Faculty of Mathematics and Computer Science', 'Факультет математики та інформатики', 'FMI', 'FACULTY', 1, 1, 'chnu.fmi', TRUE),
    (10, 'Faculty of Pedagogy, Psychology and Social Work', 'Факультет педагогіки, психології та соціальної роботи', 'FPPSW', 'FACULTY', 1, 1, 'chnu.fppsw', TRUE),
    (11, 'Faculty of Physical Education, Sports and Rehabilitation', 'Факультет фізичної культури, спорту та реабілітації', 'FPESR', 'FACULTY', 1, 1, 'chnu.fpesr', TRUE),
    (12, 'Faculty of Philology', 'Філологічний факультет', 'FP', 'FACULTY', 1, 1, 'chnu.fp', TRUE),
    (13, 'Faculty of Law', 'Юридичний факультет', 'FL', 'FACULTY', 1, 1, 'chnu.fl', TRUE);

-- ============================================================================
-- DEPARTMENTS - Institute of Biology, Chemistry and Bioresources
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (20, 'Department of Biochemistry and Biotechnology', 'Кафедра біохімії та біотехнології', 'DBBT', 'DEPARTMENT', 2, 2, 'chnu.ibcb.dbbt', TRUE),
    (21, 'Department of Botany, Forestry and Horticulture', 'Кафедра ботаніки та природоохоронної діяльності', 'DBFH', 'DEPARTMENT', 2, 2, 'chnu.ibcb.dbfh', TRUE),
    (22, 'Department of Ecology and Biomonitoring', 'Кафедра екології та біомоніторингу', 'DEBM', 'DEPARTMENT', 2, 2, 'chnu.ibcb.debm', TRUE),
    (23, 'Department of Geomatics, Land and Agricultural Management', 'Кафедра геоматики, землеустрою та агроменеджменту', 'DGLAM', 'DEPARTMENT', 2, 2, 'chnu.ibcb.dglam', TRUE),
    (24, 'Department of Molecular Genetics and Biotechnology', 'Кафедра молекулярної генетики та біотехнології', 'DMGB', 'DEPARTMENT', 2, 2, 'chnu.ibcb.dmgb', TRUE),
    (25, 'Department of Chemistry and Food Expertise', 'Кафедра хімії та експертизи харчової продукції', 'DCFE', 'DEPARTMENT', 2, 2, 'chnu.ibcb.dcfe', TRUE);

-- ============================================================================
-- DEPARTMENTS - Institute of Physical, Technical and Computer Sciences
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (26, 'Department of Electronics and Power Engineering', 'Кафедра електроніки і енергетики', 'DEPE', 'DEPARTMENT', 3, 2, 'chnu.iptcs.depe', TRUE),
    (27, 'Department of Correlation Optics', 'Кафедра кореляційної оптики', 'DCO', 'DEPARTMENT', 3, 2, 'chnu.iptcs.dco', TRUE),
    (28, 'Department of Optics, Publishing and Printing', 'Кафедра оптики та видавничо-поліграфічної справи', 'DOPP', 'DEPARTMENT', 3, 2, 'chnu.iptcs.dopp', TRUE),
    (29, 'Department of Radio Engineering and Information Security', 'Кафедра радіотехніки та інформаційної безпеки', 'DREIS', 'DEPARTMENT', 3, 2, 'chnu.iptcs.dreis', TRUE),
    (30, 'Department of Computer Sciences', 'Кафедра комп’ютерних наук', 'DCS', 'DEPARTMENT', 3, 2, 'chnu.iptcs.dcs', TRUE),
    (31, 'Department of Computer Systems and Networks', 'Кафедра комп''ютерних систем та мереж', 'DCSN', 'DEPARTMENT', 3, 2, 'chnu.iptcs.dcsn', TRUE),
    (32, 'Department of Mathematical Problems of Control and Cybernetics', 'Кафедра математичних проблем управління і кібернетики', 'DMPC', 'DEPARTMENT', 3, 2, 'chnu.iptcs.dmpc', TRUE),
    (33, 'Department of Computer Systems Software', 'Кафедра програмного забезпечення комп’ютерних систем', 'DCSS', 'DEPARTMENT', 3, 2, 'chnu.iptcs.dcss', TRUE),
    (34, 'Department of Information Technologies and Computer Physics', 'Кафедра інформаційних технологій та комп’ютерної фізики', 'DITCP', 'DEPARTMENT', 3, 2, 'chnu.iptcs.ditcp', TRUE),
    (35, 'Department of Professional and Technological Education and General Physics', 'Кафедра професійної та технологічної освіти і загальної фізики', 'DPTEGP', 'DEPARTMENT', 3, 2, 'chnu.iptcs.dptegp', TRUE),
    (36, 'Department of Thermoelectricity and Medical Physics', 'Кафедра термоелектрики та медичної фізики', 'DTMP', 'DEPARTMENT', 3, 2, 'chnu.iptcs.dtmp', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of Architecture, Construction, Decorative and Applied Arts
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (37, 'Department of Architecture and Conservation of UNESCO World Heritage Sites', 'Кафедра архітектури, урбаністики та збереження об`єктів ЮНЕСКО', 'DARCH', 'DEPARTMENT', 4, 2, 'chnu.fab.darch', TRUE),
    (38, 'Department of Construction', 'Кафедра будівництва', 'DCONST', 'DEPARTMENT', 4, 2, 'chnu.fab.dconst', TRUE),
    (39, 'Department of Decorative and Applied, and Fine Arts', 'Кафедра декоративно-прикладного та образотворчого мистецтва', 'DDAF', 'DEPARTMENT', 4, 2, 'chnu.fab.ddaf', TRUE),
    (40, 'Department of Town Planning and Architectural Design', 'Кафедра містобудування та архітектурного проєктування', 'DTPAD', 'DEPARTMENT', 4, 2, 'chnu.fab.dtpad', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of Geography
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (41, 'Department of Geography and Tourism Management', 'Кафедра географії та менеджменту туризму', 'DGTM', 'DEPARTMENT', 5, 2, 'chnu.fg.dgtm', TRUE),
    (42, 'Department of Geography of Ukraine and Regional Studies', 'Кафедра географії України та регіоналістики', 'DGURS', 'DEPARTMENT', 5, 2, 'chnu.fg.dgurs', TRUE),
    (43, 'Department of Economic Geography and Environmental Management', 'Кафедра економічної географії та екологічного менеджменту', 'DEGEM', 'DEPARTMENT', 5, 2, 'chnu.fg.degem', TRUE),
    (44, 'Department of Physical Geography, Geomorphology and Paleogeography', 'Кафедра фізичної географії, геоморфології та палеогеографії', 'DPGGP', 'DEPARTMENT', 5, 2, 'chnu.fg.dpggp', TRUE),
    (45, 'Department of Geodesy, Cartography and Territory Management', 'Кафедра геодезії, картографії та управління територіями', 'DGCTM', 'DEPARTMENT', 5, 2, 'chnu.fg.dgctm', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of Economics
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (46, 'Department of Business and Human Resource Management', 'Кафедра бізнесу та управління персоналом', 'DBHRM', 'DEPARTMENT', 6, 2, 'chnu.fe.dbhrm', TRUE),
    (47, 'Department of Economic and Mathematical Modeling', 'Кафедра економіко-математичного моделювання', 'DEMM', 'DEPARTMENT', 6, 2, 'chnu.fe.demm', TRUE),
    (48, 'Department of Economic Theory, Management and Administration', 'Кафедра економічної теорії, менеджменту та адміністрування', 'DETMA', 'DEPARTMENT', 6, 2, 'chnu.fe.detma', TRUE),
    (49, 'Department of Marketing, Innovations and Regional Development', 'Кафедра маркетингу, інновацій та регіонального розвитку', 'DMIRD', 'DEPARTMENT', 6, 2, 'chnu.fe.dmird', TRUE),
    (50, 'Department of International Economics', 'Кафедра міжнародної економіки', 'DIE', 'DEPARTMENT', 6, 2, 'chnu.fe.die', TRUE),
    (51, 'Department of Accounting, Analysis and Audit', 'Кафедра обліку, аналізу і аудиту', 'DAAA', 'DEPARTMENT', 6, 2, 'chnu.fe.daaa', TRUE),
    (52, 'Department of Finance and Credit', 'Кафедра фінансів і кредиту', 'DFC', 'DEPARTMENT', 6, 2, 'chnu.fe.dfc', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of Foreign Languages
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (53, 'Department of English', 'Кафедра англійської мови', 'DENG', 'DEPARTMENT', 7, 2, 'chnu.ffl.deng', TRUE),
    (54, 'Department of Germanic, General and Comparative Linguistics', 'Кафедра германської філології та перекладу', 'DGGC', 'DEPARTMENT', 7, 2, 'chnu.ffl.dgcc', TRUE),
    (55, 'Department of Foreign Languages for Humanities Faculties', 'Кафедра іноземних мов для гуманітарних факультетів', 'DFLHF', 'DEPARTMENT', 7, 2, 'chnu.ffl.dflhf', TRUE),
    (56, 'Department of Foreign Languages for Natural Faculties', 'Кафедра іноземних мов для природничих факультетів', 'DFLNF', 'DEPARTMENT', 7, 2, 'chnu.ffl.dflnf', TRUE),
    (57, 'Department of Communicative Linguistics and Translation', 'Кафедра лінгвістики та перекладу', 'DCLT', 'DEPARTMENT', 7, 2, 'chnu.ffl.dclt', TRUE),
    (58, 'Department of Romance Philology and Translation', 'Кафедра романської філології та перекладу', 'DRPT', 'DEPARTMENT', 7, 2, 'chnu.ffl.drpt', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of History, Political Science and International Relations
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (59, 'Department of World History', 'Кафедра всесвітньої історії', 'DWH', 'DEPARTMENT', 8, 2, 'chnu.fhpir.dwh', TRUE),
    (60, 'Department of History of Ukraine', 'Кафедра історії України', 'DHU', 'DEPARTMENT', 8, 2, 'chnu.fhpir.dhu', TRUE),
    (61, 'Department of International Relations and Public Communications', 'Кафедра міжнародних відносин та суспільних комунікацій', 'DIRPC', 'DEPARTMENT', 8, 2, 'chnu.fhpir.dirpc', TRUE),
    (62, 'Department of Political Science and Public Administration', 'Кафедра політології та державного управління', 'DPSPA', 'DEPARTMENT', 8, 2, 'chnu.fhpir.dpspa', TRUE),
    (63, 'Department of Modern Foreign Languages and Translation', 'Кафедра сучасних іноземних мов та перекладу', 'DMFLT', 'DEPARTMENT', 8, 2, 'chnu.fhpir.dmflt', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of Mathematics and Computer Science
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (64, 'Department of Algebra and Informatics', 'Кафедра алгебри та інформатики', 'DAI', 'DEPARTMENT', 9, 2, 'chnu.fmi.dai', TRUE),
    (65, 'Department of Differential Equations', 'Кафедра диференціальних рівнянь', 'DDE', 'DEPARTMENT', 9, 2, 'chnu.fmi.dde', TRUE),
    (66, 'Department of Mathematical Analysis', 'Кафедра математичного аналізу', 'DMA', 'DEPARTMENT', 9, 2, 'chnu.fmi.dma', TRUE),
    (67, 'Department of Mathematical Modelling', 'Кафедра математичного моделювання', 'DMM', 'DEPARTMENT', 9, 2, 'chnu.fmi.dmm', TRUE),
    (68, 'Department of Applied Mathematics and Information Technologies', 'Кафедра прикладної математики та інформаційних технологій', 'DAMIT', 'DEPARTMENT', 9, 2, 'chnu.fmi.damit', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of Pedagogy, Psychology and Social Work
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (69, 'Department of Pedagogy and Social Work', 'Кафедра педагогіки та соціальної роботи', 'DPSW', 'DEPARTMENT', 10, 2, 'chnu.fppsw.dpsw', TRUE),
    (70, 'Department of Music', 'Кафедра музики', 'DMUS', 'DEPARTMENT', 10, 2, 'chnu.fppsw.dmus', TRUE),
    (71, 'Department of Pedagogy and Methodology of Primary Education', 'Кафедра педагогіки та методики початкової освіти', 'DPMPE', 'DEPARTMENT', 10, 2, 'chnu.fppsw.dpmpe', TRUE),
    (72, 'Department of Pedagogy and Psychology of Preschool Education', 'Кафедра педагогіки і психології дошкільної та спеціальної освіти', 'DPPPE', 'DEPARTMENT', 10, 2, 'chnu.fppsw.dpppe', TRUE),
    (73, 'Department of Practical Psychology', 'Кафедра практичної психології', 'DPPSY', 'DEPARTMENT', 10, 2, 'chnu.fppsw.dppsy', TRUE),
    (74, 'Department of Psychology', 'Кафедра психології', 'DPSY', 'DEPARTMENT', 10, 2, 'chnu.fppsw.dpsy', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of Physical Education, Sports and Rehabilitation
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (75, 'Department of Military Training', 'Кафедра військової підготовки', 'DMT', 'DEPARTMENT', 11, 2, 'chnu.fpesr.dmt', TRUE),
    (76, 'Department of Physical Rehabilitation, Occupational Therapy and Pre-medical Assistance', 'Кафедра терапії, реабілітації та здоровʼязбережувальних технологій', 'DPROT', 'DEPARTMENT', 11, 2, 'chnu.fpesr.dprot', TRUE),
    (77, 'Department of Theory and Methods of Physical Education and Sports', 'Кафедра теорії та методики фізичної культури', 'DTMPE', 'DEPARTMENT', 11, 2, 'chnu.fpesr.dtmpe', TRUE),
    (78, 'Department of Sports and Fitness', 'Кафедра спорту та фітнесу', 'DSF', 'DEPARTMENT', 11, 2, 'chnu.fpesr.dsf', TRUE),
    (79, 'Department of Physical Education', 'Кафедра фізичного виховання', 'DPE', 'DEPARTMENT', 11, 2, 'chnu.fpesr.dpe', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of Philology
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (80, 'Department of Journalism', 'Кафедра журналістики', 'DJOUR', 'DEPARTMENT', 12, 2, 'chnu.fp.djour', TRUE),
    (81, 'Department of Foreign Literature and Literary Theory', 'Кафедра зарубіжної літератури та теорії літератури', 'DFLLT', 'DEPARTMENT', 12, 2, 'chnu.fp.dfllt', TRUE),
    (82, 'Department of History and Culture of the Ukrainian Language', 'Кафедра історії та культури української мови', 'DHCUL', 'DEPARTMENT', 12, 2, 'chnu.fp.dhcul', TRUE),
    (83, 'Department of Romanian and Classical Philology', 'Кафедра румунської та класичної філології', 'DRCP', 'DEPARTMENT', 12, 2, 'chnu.fp.drcp', TRUE),
    (84, 'Department of Modern Ukrainian Language', 'Кафедра сучасної української мови', 'DMUL', 'DEPARTMENT', 12, 2, 'chnu.fp.dmul', TRUE),
    (85, 'Department of Ukrainian Literature', 'Кафедра української літератури', 'DULIT', 'DEPARTMENT', 12, 2, 'chnu.fp.dulit', TRUE),
    (86, 'Department of Philosophy and Cultural Studies', 'Кафедра філософії та культурології', 'DPCS', 'DEPARTMENT', 12, 2, 'chnu.fp.dpcs', TRUE);

-- ============================================================================
-- DEPARTMENTS - Faculty of Law
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (87, 'Department of Public Law', 'Кафедра публічного права', 'DPUBL', 'DEPARTMENT', 13, 2, 'chnu.fl.dpubl', TRUE),
    (88, 'Department of Criminal Law', 'Кафедра кримінального права', 'DCRIM', 'DEPARTMENT', 13, 2, 'chnu.fl.dcrim', TRUE),
    (89, 'Department of International Law and Comparative Jurisprudence', 'Кафедра міжнародного права та порівняльного правознавства', 'DILCJ', 'DEPARTMENT', 13, 2, 'chnu.fl.dilcj', TRUE),
    (90, 'Department of Procedural Law', 'Кафедра процесуального права', 'DPROC', 'DEPARTMENT', 13, 2, 'chnu.fl.dproc', TRUE),
    (91, 'Department of Theory of Law and Human Rights', 'Кафедра теорії права та прав людини', 'DTLHR', 'DEPARTMENT', 13, 2, 'chnu.fl.dtlhr', TRUE),
    (92, 'Department of Private Law', 'Кафедра приватного права', 'DPRIV', 'DEPARTMENT', 13, 2, 'chnu.fl.dpriv', TRUE);

-- ============================================================================
-- SPECIALITIES - Applied College
-- ============================================================================
INSERT INTO organizations (org_id, name, name_uk, code, org_type, parent_org_id, depth, hierarchy_path, is_active)
VALUES
    (93, 'Accounting and Taxation', 'Облік і оподаткування', 'SAT', 'SPECIALITY', 14, 2, 'chnu.ac.sat', TRUE),
    (94, 'Finance, Banking, Insurance and Stock Market', 'Фінанси, банківська справа, страхування та фондовий ринок', 'SFBISM', 'SPECIALITY', 14, 2, 'chnu.ac.sfbism', TRUE),
    (95, 'Management', 'Менеджмент', 'SM', 'SPECIALITY', 14, 2, 'chnu.ac.sm', TRUE),
    (96, 'Secretarial and Office Work', 'Секретарська та офісна справа (ОПП "Правнична діяльність")', 'SSOW', 'SPECIALITY', 14, 2, 'chnu.ac.ssow', TRUE),
    (97, 'Computer Science', 'Компʼютерні науки', 'SCS', 'SPECIALITY', 14, 2, 'chnu.ac.scs', TRUE),
    (98, 'Computer Engineering', 'Компʼютерна інженерія', 'SCE', 'SPECIALITY', 14, 2, 'chnu.ac.sce', TRUE),
    (99, 'Journalism', 'Журналістика', 'SJ', 'SPECIALITY', 14, 2, 'chnu.ac.sj', TRUE);


-- Reset sequence to next available
SELECT setval('organizations_org_id_seq', COALESCE((SELECT MAX(org_id) FROM organizations), 1));

-- Re-enable triggers
ALTER TABLE organizations ENABLE TRIGGER trg_organizations_audit;
