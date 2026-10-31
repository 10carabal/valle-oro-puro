-- Datos de ejemplo: un ADMIN y un USER (contraseñas en claro: admin123 y usuario123;
-- los hashes son BCrypt con strength 10, generados con BCryptPasswordEncoder).
INSERT INTO auth.usuarios (email, password_hash, nombre, rol, creado_en) VALUES
    ('admin@valle.test',
     '$2a$10$qMVdSh7BcvaC7GimyGIL/e.lWyAkEZOe6VL.U9DExhnGD9h/C3hu.',
     'Admin Valle', 'ADMIN', now()),
    ('usuario@valle.test',
     '$2a$10$SoQKLwRDjq92Vc1Ih82DCeBeJ9YBtD3timfnQDxjEFR.GhhKAfSfu',
     'Usuario Demo', 'USER', now())
ON CONFLICT (email) DO NOTHING;
