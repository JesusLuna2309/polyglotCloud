-- Insertar lenguajes de programación populares
INSERT INTO languages (id, name, extension, mime_type, is_active) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'Java', '.java', 'text/x-java', true),
    ('550e8400-e29b-41d4-a716-446655440002', 'Python', '.py', 'text/x-python', true),
    ('550e8400-e29b-41d4-a716-446655440003', 'JavaScript', '.js', 'text/javascript', true),
    ('550e8400-e29b-41d4-a716-446655440004', 'TypeScript', '.ts', 'text/typescript', true),
    ('550e8400-e29b-41d4-a716-446655440005', 'C++', '.cpp', 'text/x-c++src', true),
    ('550e8400-e29b-41d4-a716-446655440006', 'C#', '.cs', 'text/x-csharp', true),
    ('550e8400-e29b-41d4-a716-446655440007', 'Go', '.go', 'text/x-go', true),
    ('550e8400-e29b-41d4-a716-446655440008', 'Rust', '.rs', 'text/x-rust', true),
    ('550e8400-e29b-41d4-a716-446655440009', 'PHP', '.php', 'text/x-php', true),
    ('550e8400-e29b-41d4-a716-446655440010', 'Ruby', '.rb', 'text/x-ruby', true),
    ('550e8400-e29b-41d4-a716-446655440011', 'Swift', '.swift', 'text/x-swift', true),
    ('550e8400-e29b-41d4-a716-446655440012', 'Kotlin', '.kt', 'text/x-kotlin', true),
    ('550e8400-e29b-41d4-a716-446655440013', 'SQL', '.sql', 'text/x-sql', true),
    ('550e8400-e29b-41d4-a716-446655440014', 'HTML', '.html', 'text/html', true),
    ('550e8400-e29b-41d4-a716-446655440015', 'CSS', '.css', 'text/css', true),
    ('550e8400-e29b-41d4-a716-446655440016', 'JSON', '.json', 'application/json', true),
    ('550e8400-e29b-41d4-a716-446655440017', 'XML', '.xml', 'text/xml', true),
    ('550e8400-e29b-41d4-a716-446655440018', 'YAML', '.yaml', 'text/yaml', true),
    ('550e8400-e29b-41d4-a716-446655440019', 'Bash', '.sh', 'text/x-shellscript', true),
    ('550e8400-e29b-41d4-a716-446655440020', 'PowerShell', '.ps1', 'text/x-powershell', true);

-- Insertar usuario administrador (contraseña: admin123)
-- Hash generado con PostQuantumPasswordEncoder para "admin123"
INSERT INTO users (
    id, 
    email, 
    username, 
    password_hash, 
    role, 
    is_active, 
    email_verified,
    created_at,
    updated_at
) VALUES (
    '550e8400-e29b-41d4-a716-446655440100',
    'admin@polyglotcloud.com',
    'admin',
    '$argon2id$v=19$m=65536,t=3,p=4$randomsalthere$hashedpasswordhere',
    'ADMIN',
    true,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Insertar usuarios de prueba
INSERT INTO users (
    id, 
    email, 
    username, 
    password_hash, 
    role, 
    is_active, 
    email_verified
) VALUES 
    (
        '550e8400-e29b-41d4-a716-446655440101',
        'john.doe@example.com',
        'johndoe',
        '$argon2id$v=19$m=65536,t=3,p=4$randomsalthere$hashedpasswordhere',
        'USER',
        true,
        true
    ),
    (
        '550e8400-e29b-41d4-a716-446655440102',
        'jane.smith@example.com',
        'janesmith',
        '$argon2id$v=19$m=65536,t=3,p=4$randomsalthere$hashedpasswordhere',
        'USER',
        true,
        false
    );

-- Insertar snippets de ejemplo
INSERT INTO snippets (
    id,
    title,
    content,
    description,
    user_id,
    language_id,
    status,
    is_public
) VALUES 
    (
        '550e8400-e29b-41d4-a716-446655440200',
        'Hello World Java',
        'public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}',
        'Classic Hello World example in Java',
        '550e8400-e29b-41d4-a716-446655440100',
        '550e8400-e29b-41d4-a716-446655440001',
        'PUBLISHED',
        true
    ),
    (
        '550e8400-e29b-41d4-a716-446655440201',
        'Python Fibonacci',
        'def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

# Test the function
for i in range(10):
    print(f"F({i}) = {fibonacci(i)}")',
        'Recursive Fibonacci implementation in Python',
        '550e8400-e29b-41d4-a716-446655440101',
        '550e8400-e29b-41d4-a716-446655440002',
        'PUBLISHED',
        true
    ),
    (
        '550e8400-e29b-41d4-a716-446655440202',
        'JavaScript Array Methods',
        'const numbers = [1, 2, 3, 4, 5];

// Map, Filter, Reduce examples
const doubled = numbers.map(n => n * 2);
const evens = numbers.filter(n => n % 2 === 0);
const sum = numbers.reduce((acc, n) => acc + n, 0);

console.log("Doubled:", doubled);
console.log("Evens:", evens);  
console.log("Sum:", sum);',
        'Common JavaScript array manipulation methods',
        '550e8400-e29b-41d4-a716-446655440101',
        '550e8400-e29b-41d4-a716-446655440003',
        'DRAFT',
        false
    );