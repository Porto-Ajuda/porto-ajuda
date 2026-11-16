package com.portoajuda.aplicacao_osc.service;

/** Internal event emitted only for a newly inserted submission-version job. */
public record VerificacaoAiJobEnqueuedEvent(long jobId) { }
