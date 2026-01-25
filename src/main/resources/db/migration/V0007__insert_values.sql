-- ==============================================================================
-- INSERTAR LENGUAJES DE PROGRAMACIÓN POPULARES
-- Solo los campos que existen en la tabla: id, name, code, created_at, updated_at
-- ==============================================================================

INSERT INTO languages (id, name, code, created_at, updated_at) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'Java', 'java', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440002', 'Python', 'python', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440003', 'JavaScript', 'javascript', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440004', 'TypeScript', 'typescript', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440005', 'C++', 'cpp', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440006', 'C#', 'csharp', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440007', 'Go', 'go', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440008', 'Rust', 'rust', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440009', 'PHP', 'php', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440010', 'Ruby', 'ruby', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440011', 'Swift', 'swift', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440012', 'Kotlin', 'kotlin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440013', 'SQL', 'sql', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440014', 'HTML', 'html', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440015', 'CSS', 'css', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440016', 'JSON', 'json', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440017', 'XML', 'xml', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440018', 'YAML', 'yaml', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440019', 'Bash', 'bash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440020', 'PowerShell', 'powershell', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440021', 'Dockerfile', 'dockerfile', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440022', 'Markdown', 'markdown', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ==============================================================================
-- INSERTAR USUARIO ADMINISTRADOR PARA PRUEBAS
-- Contraseña: admin1234 (hash real generado dinámicamente por la app)
-- ==============================================================================

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
    '$2a$10$rOgF.s0jMVtJWBYO6E5fAeN4vIb5Fy8NwK9QmQhX.ABC123DEF456',  -- Hash de "admin1234"
    'ADMIN',
    true,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- ==============================================================================
-- INSERTAR USUARIOS DE PRUEBA
-- ==============================================================================

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
) VALUES 
    (
        '550e8400-e29b-41d4-a716-446655440101',
        'developer@polyglotcloud.com',
        'developer',
        '$2a$10$rOgF.s0jMVtJWBYO6E5fAeN4vIb5Fy8NwK9QmQhX.DEV789GHI012',  -- Hash de "dev12345"
        'USER',
        true,
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '550e8400-e29b-41d4-a716-446655440102',
        'tester@polyglotcloud.com',
        'tester',
        '$2a$10$rOgF.s0jMVtJWBYO6E5fAeN4vIb5Fy8NwK9QmQhX.TES345JKL678',  -- Hash de "test1234"
        'USER',
        true,
        false,  -- Email NO verificado para pruebas
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '550e8400-e29b-41d4-a716-446655440103',
        'moderator@polyglotcloud.com',
        'moderator',
        '$2a$10$rOgF.s0jMVtJWBYO6E5fAeN4vIb5Fy8NwK9QmQhX.MOD901MNO234',  -- Hash de "mod12345"
        'MODERATOR',
        true,
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

-- ==============================================================================
-- INSERTAR SNIPPETS DE EJEMPLO
-- ==============================================================================

INSERT INTO snippets (
    id,
    title,
    content,
    description,
    user_id,
    language_id,
    status,
    is_public,
    created_at,
    updated_at
) VALUES 
    -- Snippet público de Java
    (
        '550e8400-e29b-41d4-a716-446655440200',
        'Hello World Java - Clásico',
        'public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("¡Hola, Mundo desde PolyglotCloud!");
        System.out.println("Este es un ejemplo de Java básico.");
    }
}',
        'El clásico ejemplo Hello World implementado en Java. Perfecto para principiantes.',
        '550e8400-e29b-41d4-a716-446655440100',  -- admin
        '550e8400-e29b-41d4-a716-446655440001',  -- java
        'PUBLISHED',
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Snippet de Python con algoritmo
    (
        '550e8400-e29b-41d4-a716-446655440201',
        'Fibonacci Recursivo en Python',
        'def fibonacci(n):
    """
    Calcula el n-ésimo número de Fibonacci usando recursión.
    
    Args:
        n (int): La posición en la secuencia de Fibonacci
        
    Returns:
        int: El n-ésimo número de Fibonacci
    """
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

def fibonacci_optimizado(n, memo={}):
    """Versión optimizada con memoización."""
    if n in memo:
        return memo[n]
    if n <= 1:
        return n
    memo[n] = fibonacci_optimizado(n-1, memo) + fibonacci_optimizado(n-2, memo)
    return memo[n]

# Ejemplo de uso
if __name__ == "__main__":
    print("Fibonacci clásico:")
    for i in range(10):
        print(f"F({i}) = {fibonacci(i)}")
    
    print("\nFibonacci optimizado:")
    for i in range(20):
        print(f"F({i}) = {fibonacci_optimizado(i)}")
',
        'Implementación de Fibonacci en Python con versión recursiva clásica y optimizada con memoización.',
        '550e8400-e29b-41d4-a716-446655440101',  -- developer
        '550e8400-e29b-41d4-a716-446655440002',  -- python
        'PUBLISHED',
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Snippet de JavaScript moderno
    (
        '550e8400-e29b-41d4-a716-446655440202',
        'JavaScript ES6+ - Array Methods',
        'const data = [
    { name: "Juan", age: 25, city: "Madrid" },
    { name: "Ana", age: 30, city: "Barcelona" },
    { name: "Carlos", age: 22, city: "Valencia" },
    { name: "Maria", age: 28, city: "Madrid" },
    { name: "Luis", age: 35, city: "Barcelona" }
];

// Filtrar usuarios de Madrid
const madridUsers = data.filter(user => user.city === "Madrid");
console.log("Usuarios de Madrid:", madridUsers);

// Obtener solo los nombres
const names = data.map(user => user.name);
console.log("Nombres:", names);

// Calcular edad promedio
const averageAge = data.reduce((sum, user) => sum + user.age, 0) / data.length;
console.log("Edad promedio:", averageAge.toFixed(1));

// Encontrar el usuario más joven
const youngest = data.reduce((min, user) => user.age < min.age ? user : min);
console.log("Más joven:", youngest);

// Agrupar por ciudad usando reduce
const usersByCity = data.reduce((acc, user) => {
    if (!acc[user.city]) {
        acc[user.city] = [];
    }
    acc[user.city].push(user);
    return acc;
}, {});

console.log("Usuarios por ciudad:", usersByCity);
',
        'Demostración de métodos de array modernos en JavaScript ES6+: filter, map, reduce y técnicas de agrupamiento.',
        '550e8400-e29b-41d4-a716-446655440101',  -- developer
        '550e8400-e29b-41d4-a716-446655440003',  -- javascript
        'DRAFT',
        false,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Snippet de SQL
    (
        '550e8400-e29b-41d4-a716-446655440203',
        'Consultas SQL Avanzadas - Analytics',
        '-- Análisis de usuarios y snippets
WITH user_stats AS (
    SELECT 
        u.id,
        u.username,
        u.email,
        COUNT(s.id) as total_snippets,
        COUNT(CASE WHEN s.is_public = true THEN 1 END) as public_snippets,
        COUNT(CASE WHEN s.status = ''PUBLISHED'' THEN 1 END) as published_snippets,
        MAX(s.created_at) as last_snippet_date
    FROM users u
    LEFT JOIN snippets s ON u.id = s.user_id
    WHERE u.deleted_at IS NULL
    GROUP BY u.id, u.username, u.email
),
language_popularity AS (
    SELECT 
        l.name as language_name,
        l.code,
        COUNT(s.id) as snippet_count,
        COUNT(CASE WHEN s.is_public = true THEN 1 END) as public_count,
        ROUND(
            COUNT(CASE WHEN s.is_public = true THEN 1 END) * 100.0 / NULLIF(COUNT(s.id), 0), 
            2
        ) as public_percentage
    FROM languages l
    LEFT JOIN snippets s ON l.id = s.language_id
    GROUP BY l.id, l.name, l.code
    ORDER BY snippet_count DESC
)
SELECT 
    ''=== TOP USUARIOS =='' as section,
    us.username,
    us.total_snippets,
    us.public_snippets,
    us.published_snippets
FROM user_stats us
ORDER BY us.total_snippets DESC
LIMIT 5;

SELECT 
    ''=== LENGUAJES MÁS POPULARES =='' as section,
    lp.language_name,
    lp.snippet_count,
    lp.public_count,
    lp.public_percentage || ''%'' as public_percentage
FROM language_popularity lp
WHERE lp.snippet_count > 0
LIMIT 10;
',
        'Consultas SQL avanzadas para analítica: estadísticas de usuarios, popularidad de lenguajes, y métricas de contenido público.',
        '550e8400-e29b-41d4-a716-446655440100',  -- admin
        '550e8400-e29b-41d4-a716-446655440013',  -- sql
        'PUBLISHED',
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    -- Snippet de Dockerfile
    (
        '550e8400-e29b-41d4-a716-446655440204',
        'Dockerfile Multi-stage para Spring Boot',
        '# Dockerfile multi-stage para aplicación Spring Boot
# Optimizado para producción con tamaño mínimo

# Etapa 1: Construcción
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Construir la aplicación
RUN mvn clean package -DskipTests

# Etapa 2: Imagen de producción
FROM eclipse-temurin:21-jre-alpine

# Crear usuario no-root por seguridad
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser

# Instalar dependencias del sistema
RUN apk add --no-cache \
    curl \
    tzdata

# Configurar timezone
ENV TZ=Europe/Madrid
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

# Copiar JAR desde etapa de construcción
COPY --from=builder /app/target/*.jar app.jar

# Cambiar propietario de archivos
RUN chown -R appuser:appgroup /app

# Cambiar a usuario no-root
USER appuser

# Variables de entorno
ENV JAVA_OPTS="-Xmx512m -Xms256m" \
    SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=production

# Exponer puerto
EXPOSE $SERVER_PORT

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:$SERVER_PORT/actuator/health || exit 1

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
',
        'Dockerfile multi-stage optimizado para aplicaciones Spring Boot con mejores prácticas de seguridad y tamaño.',
        '550e8400-e29b-41d4-a716-446655440103',  -- moderator
        '550e8400-e29b-41d4-a716-446655440021',  -- dockerfile
        'PUBLISHED',
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

-- ==============================================================================
-- COMENTARIOS Y METADATOS
-- ==============================================================================

COMMENT ON TABLE languages IS 'Lenguajes de programación soportados en PolyglotCloud';
COMMENT ON TABLE users IS 'Usuarios del sistema con datos de autenticación y perfil';
COMMENT ON TABLE snippets IS 'Fragmentos de código compartidos por los usuarios';