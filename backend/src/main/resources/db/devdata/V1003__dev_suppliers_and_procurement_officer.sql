-- Development sample: a procurement officer and a few fictitious suppliers.
-- KRA PINs below are made up and do not belong to real taxpayers.

INSERT INTO platform.users (id, tenant_id, email, display_name)
VALUES ('01920000-0000-7000-8000-000000000106', '01920000-0000-7000-8000-000000000001',
        'po@example.com', 'Sample Procurement Officer');

INSERT INTO platform.user_roles (user_id, role_code)
VALUES ('01920000-0000-7000-8000-000000000106', 'PROCUREMENT_OFFICER');

INSERT INTO procurement.suppliers
    (id, tenant_id, name, kra_pin, registration_no, email, phone, categories,
     agpo_category, agpo_certificate_no, agpo_expiry, status, created_at, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000005001', '01920000-0000-7000-8000-000000000001',
     'Sample Transformers Ltd', 'P000000001A', 'CPR/2010/000001', 'bids@sample-transformers.example',
     '+254700000001', 'Transformers; Switchgear', NULL, NULL, NULL, 'ACTIVE', now(), now()),
    ('01920000-0000-7000-8000-000000005002', '01920000-0000-7000-8000-000000000001',
     'Sample Cables & Conductors Ltd', 'P000000002B', 'CPR/2012/000002', 'tenders@sample-cables.example',
     '+254700000002', 'Cables; Conductors', NULL, NULL, NULL, 'ACTIVE', now(), now()),
    ('01920000-0000-7000-8000-000000005003', '01920000-0000-7000-8000-000000000001',
     'Sample Youth Electrical Works', 'P000000003C', 'BN/2021/000003', 'info@sample-youth.example',
     '+254700000003', 'Electrical installation', 'YOUTH', 'AGPO/Y/0003', '2027-06-30', 'ACTIVE', now(), now()),
    ('01920000-0000-7000-8000-000000005004', '01920000-0000-7000-8000-000000000001',
     'Sample Metering Solutions', 'P000000004D', 'CPR/2015/000004', 'sales@sample-metering.example',
     '+254700000004', 'Meters; Metering software', 'WOMEN', 'AGPO/W/0004', '2027-03-31', 'ACTIVE', now(), now()),
    ('01920000-0000-7000-8000-000000005005', '01920000-0000-7000-8000-000000000001',
     'Sample Debarred Supplies', 'P000000005E', NULL, NULL, NULL, 'General supplies',
     NULL, NULL, NULL, 'DEBARRED', now(), now());
