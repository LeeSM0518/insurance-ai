package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.PolicyEmbeddingEntity

/**
 * pgvector vector 타입 INSERT를 위한 커스텀 Repository.
 * R2DBC 자동 생성 쿼리는 FloatArray를 varchar로 바인딩하여
 * vector 컬럼에 INSERT 시 타입 불일치 에러 발생.
 * 명시적 ::vector 캐스팅으로 해결.
 */
interface PolicyEmbeddingRepositoryCustom {
    suspend fun saveWithVector(entity: PolicyEmbeddingEntity): PolicyEmbeddingEntity
}
