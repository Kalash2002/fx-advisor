CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Spring AI will use this table for RAG (pgvector similarity search)
CREATE TABLE IF NOT EXISTS vector_store (
                                            id        UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   TEXT    NOT NULL,
    metadata  JSONB,
    embedding VECTOR(1536)   -- OpenAI text-embedding-3-small
    );

-- IVFFlat index for approximate nearest neighbor search
CREATE INDEX IF NOT EXISTS idx_vs_embedding
    ON vector_store USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Metadata index for source filtering
CREATE INDEX IF NOT EXISTS idx_vs_metadata
    ON vector_store USING GIN (metadata);