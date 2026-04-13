-- V0012__remove_source_code_column.sql
-- Eliminar columna source_code ya que ahora usamos sourceSnippet.content

-- Primero verificar que todas las translations tienen snippet_id válido
SELECT t.id, t.snippet_id, s.id as snippet_exists 
FROM translations t 
LEFT JOIN snippets s ON t.snippet_id = s.id 
WHERE s.id IS NULL;

-- Si no hay resultados, proceder con la eliminación
-- (Si hay resultados, necesitarás limpiar datos huérfanos primero)

-- Eliminar la columna source_code
ALTER TABLE translations DROP COLUMN IF EXISTS source_code;

-- Agregar comentario para documentación
COMMENT ON TABLE translations IS 'Translation requests - source code is retrieved from sourceSnippet relation';