CREATE TABLE app_users (
    user_id AUTOINCREMENT CONSTRAINT pk_app_users PRIMARY KEY,
    google_uid TEXT(100),
    full_name TEXT(120) NOT NULL,
    email TEXT(120) NOT NULL,
    phone TEXT(30),
    is_active YESNO NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE businesses (
    business_id AUTOINCREMENT CONSTRAINT pk_businesses PRIMARY KEY,
    owner_user_id LONG NOT NULL,
    business_name TEXT(120) NOT NULL,
    main_wallet_number TEXT(30) NOT NULL,
    main_wallet_type TEXT(20) NOT NULL,
    business_status TEXT(20) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_businesses_owner
        FOREIGN KEY (owner_user_id) REFERENCES app_users (user_id)
);

CREATE TABLE stalls (
    stall_id AUTOINCREMENT CONSTRAINT pk_stalls PRIMARY KEY,
    business_id LONG NOT NULL,
    stall_name TEXT(80) NOT NULL,
    stall_description TEXT(150),
    is_active YESNO NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_stalls_business
        FOREIGN KEY (business_id) REFERENCES businesses (business_id)
);

CREATE TABLE business_members (
    member_id AUTOINCREMENT CONSTRAINT pk_business_members PRIMARY KEY,
    business_id LONG NOT NULL,
    user_id LONG NOT NULL,
    member_role TEXT(20) NOT NULL,
    member_status TEXT(20) NOT NULL,
    default_stall_id LONG,
    invited_by_user_id LONG,
    joined_at DATETIME,
    CONSTRAINT fk_members_business
        FOREIGN KEY (business_id) REFERENCES businesses (business_id),
    CONSTRAINT fk_members_user
        FOREIGN KEY (user_id) REFERENCES app_users (user_id),
    CONSTRAINT fk_members_default_stall
        FOREIGN KEY (default_stall_id) REFERENCES stalls (stall_id),
    CONSTRAINT fk_members_invited_by
        FOREIGN KEY (invited_by_user_id) REFERENCES app_users (user_id)
);

CREATE TABLE invitations (
    invitation_id AUTOINCREMENT CONSTRAINT pk_invitations PRIMARY KEY,
    business_id LONG NOT NULL,
    invitation_code TEXT(40) NOT NULL,
    target_role TEXT(20) NOT NULL,
    target_stall_id LONG,
    invited_by_user_id LONG NOT NULL,
    invitation_status TEXT(20) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_invitations_business
        FOREIGN KEY (business_id) REFERENCES businesses (business_id),
    CONSTRAINT fk_invitations_stall
        FOREIGN KEY (target_stall_id) REFERENCES stalls (stall_id),
    CONSTRAINT fk_invitations_invited_by
        FOREIGN KEY (invited_by_user_id) REFERENCES app_users (user_id)
);

CREATE TABLE work_sessions (
    work_session_id AUTOINCREMENT CONSTRAINT pk_work_sessions PRIMARY KEY,
    business_id LONG NOT NULL,
    member_id LONG NOT NULL,
    active_stall_id LONG NOT NULL,
    started_at DATETIME NOT NULL,
    ended_at DATETIME,
    session_status TEXT(20) NOT NULL,
    notes TEXT(150),
    CONSTRAINT fk_sessions_business
        FOREIGN KEY (business_id) REFERENCES businesses (business_id),
    CONSTRAINT fk_sessions_member
        FOREIGN KEY (member_id) REFERENCES business_members (member_id),
    CONSTRAINT fk_sessions_stall
        FOREIGN KEY (active_stall_id) REFERENCES stalls (stall_id)
);

CREATE TABLE thesis_transactions (
    transaction_id AUTOINCREMENT CONSTRAINT pk_thesis_transactions PRIMARY KEY,
    business_id LONG NOT NULL,
    detected_by_user_id LONG NOT NULL,
    claimed_by_member_id LONG,
    claimed_stall_id LONG,
    work_session_id LONG,
    sender_name TEXT(120) NOT NULL,
    amount CURRENCY NOT NULL,
    wallet_type TEXT(20) NOT NULL,
    reference_code TEXT(40) NOT NULL,
    transaction_status TEXT(20) NOT NULL,
    sale_description TEXT(255),
    observed_reason TEXT(255),
    detected_at DATETIME NOT NULL,
    claimed_at DATETIME,
    confirmed_at DATETIME,
    CONSTRAINT fk_transactions_business
        FOREIGN KEY (business_id) REFERENCES businesses (business_id),
    CONSTRAINT fk_transactions_detected_by
        FOREIGN KEY (detected_by_user_id) REFERENCES app_users (user_id),
    CONSTRAINT fk_transactions_claimed_by
        FOREIGN KEY (claimed_by_member_id) REFERENCES business_members (member_id),
    CONSTRAINT fk_transactions_stall
        FOREIGN KEY (claimed_stall_id) REFERENCES stalls (stall_id),
    CONSTRAINT fk_transactions_session
        FOREIGN KEY (work_session_id) REFERENCES work_sessions (work_session_id)
);

INSERT INTO app_users (google_uid, full_name, email, phone, is_active, created_at)
VALUES ('google-owner-001', 'Rosa Huaman', 'rosa@demo.com', '987654321', TRUE, #05/25/2026 08:00:00#);

INSERT INTO app_users (google_uid, full_name, email, phone, is_active, created_at)
VALUES ('google-helper-001', 'Lucia Ramos', 'lucia@demo.com', '900111222', TRUE, #05/25/2026 08:05:00#);

INSERT INTO app_users (google_uid, full_name, email, phone, is_active, created_at)
VALUES ('google-helper-002', 'Marco Salas', 'marco@demo.com', '900333444', TRUE, #05/25/2026 08:07:00#);

INSERT INTO app_users (google_uid, full_name, email, phone, is_active, created_at)
VALUES ('google-helper-003', 'Rosa Quispe', 'rosa.q@demo.com', '900555666', TRUE, #05/25/2026 08:09:00#);

INSERT INTO businesses (owner_user_id, business_name, main_wallet_number, main_wallet_type, business_status, created_at)
VALUES (1, 'Bodega Santa Rosa', '987654321', 'YAPE', 'ACTIVO', #05/25/2026 08:15:00#);

INSERT INTO stalls (business_id, stall_name, stall_description, is_active, created_at)
VALUES (1, 'Puesto 1', 'Mostrador principal', TRUE, #05/25/2026 08:20:00#);

INSERT INTO stalls (business_id, stall_name, stall_description, is_active, created_at)
VALUES (1, 'Puesto 2', 'Zona bebidas y snacks', TRUE, #05/25/2026 08:21:00#);

INSERT INTO stalls (business_id, stall_name, stall_description, is_active, created_at)
VALUES (1, 'Puesto 3', 'Pedidos por WhatsApp', TRUE, #05/25/2026 08:22:00#);

INSERT INTO business_members (business_id, user_id, member_role, member_status, default_stall_id, invited_by_user_id, joined_at)
VALUES (1, 1, 'OWNER', 'ACTIVO', Null, Null, #05/25/2026 08:25:00#);

INSERT INTO business_members (business_id, user_id, member_role, member_status, default_stall_id, invited_by_user_id, joined_at)
VALUES (1, 2, 'HELPER', 'ACTIVO', 1, 1, #05/25/2026 08:26:00#);

INSERT INTO business_members (business_id, user_id, member_role, member_status, default_stall_id, invited_by_user_id, joined_at)
VALUES (1, 3, 'HELPER', 'ACTIVO', 2, 1, #05/25/2026 08:27:00#);

INSERT INTO business_members (business_id, user_id, member_role, member_status, default_stall_id, invited_by_user_id, joined_at)
VALUES (1, 4, 'HELPER', 'ACTIVO', 3, 1, #05/25/2026 08:28:00#);

INSERT INTO invitations (business_id, invitation_code, target_role, target_stall_id, invited_by_user_id, invitation_status, expires_at, created_at)
VALUES (1, 'SANTA-AYUDA-01', 'HELPER', 1, 1, 'PENDIENTE', #05/26/2026 18:00:00#, #05/25/2026 09:00:00#);

INSERT INTO invitations (business_id, invitation_code, target_role, target_stall_id, invited_by_user_id, invitation_status, expires_at, created_at)
VALUES (1, 'SANTA-AYUDA-02', 'HELPER', 2, 1, 'ACTIVA', #05/26/2026 12:00:00#, #05/25/2026 09:15:00#);

INSERT INTO work_sessions (business_id, member_id, active_stall_id, started_at, ended_at, session_status, notes)
VALUES (1, 2, 1, #05/25/2026 07:30:00#, Null, 'ABIERTA', 'Lucia eligio Puesto 1 al iniciar turno');

INSERT INTO work_sessions (business_id, member_id, active_stall_id, started_at, ended_at, session_status, notes)
VALUES (1, 3, 2, #05/25/2026 07:30:00#, Null, 'ABIERTA', 'Marco eligio Puesto 2 al iniciar turno');

INSERT INTO work_sessions (business_id, member_id, active_stall_id, started_at, ended_at, session_status, notes)
VALUES (1, 4, 3, #05/25/2026 07:30:00#, Null, 'ABIERTA', 'Rosa eligio Puesto 3 al iniciar turno');

INSERT INTO thesis_transactions (business_id, detected_by_user_id, claimed_by_member_id, claimed_stall_id, work_session_id, sender_name, amount, wallet_type, reference_code, transaction_status, sale_description, observed_reason, detected_at, claimed_at, confirmed_at)
VALUES (1, 1, 2, 1, 1, 'Ana Perez', 12.00, 'YAPE', 'Y12345', 'CONFIRMADO', 'Desayuno y cafe', Null, #05/25/2026 07:42:00#, #05/25/2026 07:43:00#, #05/25/2026 07:45:00#);

INSERT INTO thesis_transactions (business_id, detected_by_user_id, claimed_by_member_id, claimed_stall_id, work_session_id, sender_name, amount, wallet_type, reference_code, transaction_status, sale_description, observed_reason, detected_at, claimed_at, confirmed_at)
VALUES (1, 1, 3, 2, 2, 'Luis Rojas', 7.50, 'PLIN', 'P55221', 'RECLAMADO', 'Gaseosa y snack', Null, #05/25/2026 08:05:00#, #05/25/2026 08:06:00#, Null);

INSERT INTO thesis_transactions (business_id, detected_by_user_id, claimed_by_member_id, claimed_stall_id, work_session_id, sender_name, amount, wallet_type, reference_code, transaction_status, sale_description, observed_reason, detected_at, claimed_at, confirmed_at)
VALUES (1, 1, Null, Null, Null, 'Carla Soto', 25.00, 'YAPE', 'Y33441', 'OBSERVADO', Null, 'Aun no se sabe de que puesto fue la venta', #05/25/2026 08:51:00#, Null, Null);

INSERT INTO thesis_transactions (business_id, detected_by_user_id, claimed_by_member_id, claimed_stall_id, work_session_id, sender_name, amount, wallet_type, reference_code, transaction_status, sale_description, observed_reason, detected_at, claimed_at, confirmed_at)
VALUES (1, 1, 4, 3, 3, 'Miguel Diaz', 18.00, 'YAPE', 'Y77651', 'CONFIRMADO', 'Pedido WhatsApp', Null, #05/25/2026 09:12:00#, #05/25/2026 09:13:00#, #05/25/2026 09:15:00#);

INSERT INTO thesis_transactions (business_id, detected_by_user_id, claimed_by_member_id, claimed_stall_id, work_session_id, sender_name, amount, wallet_type, reference_code, transaction_status, sale_description, observed_reason, detected_at, claimed_at, confirmed_at)
VALUES (1, 1, 3, 2, 2, 'Julia Torres', 14.00, 'PLIN', 'P99887', 'RECLAMADO', 'Menu ejecutivo', Null, #05/25/2026 09:40:00#, #05/25/2026 09:41:00#, Null);
