import { ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router';

export default function TermsPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-dvh bg-background">
      <header className="sticky top-0 z-10 flex h-12 items-center gap-3 border-b bg-background px-4">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="flex items-center justify-center"
          aria-label="뒤로 가기"
        >
          <ArrowLeft className="size-5" />
        </button>
        <h1 className="text-base font-semibold">서비스 이용약관</h1>
      </header>

      <main className="mx-auto max-w-2xl px-4 py-6 text-sm leading-relaxed text-foreground space-y-6">
        <section>
          <h2 className="text-lg font-semibold mb-3">제1조 (목적)</h2>
          <p>
            본 약관은 Insurance AI(이하 &quot;서비스&quot;)가 제공하는 보험 약관
            검색 및 AI 질의응답 서비스의 이용과 관련하여 서비스와 이용자 간의
            권리, 의무 및 책임사항을 규정함을 목적으로 합니다.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">제2조 (정의)</h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              &quot;서비스&quot;란 보험 약관 검색, AI 기반 질의응답 등 Insurance
              AI가 제공하는 모든 관련 서비스를 의미합니다.
            </li>
            <li>
              &quot;이용자&quot;란 본 약관에 따라 서비스를 이용하는 자를
              의미합니다.
            </li>
            <li>
              &quot;AI 응답&quot;이란 인공지능 모델이 보험 약관을 기반으로
              생성한 답변을 의미합니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">제3조 (서비스 이용)</h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              서비스는 카카오 계정을 통해 로그인한 이용자에게 제공됩니다.
            </li>
            <li>
              AI 응답은 참고 목적으로만 제공되며, 법률적·재무적 조언을
              대체하지 않습니다.
            </li>
            <li>
              이용자는 AI 응답의 정확성을 반드시 원본 약관과 대조하여 확인해야
              합니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">제4조 (면책사항)</h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              서비스는 AI 응답의 완전성, 정확성, 적시성을 보장하지 않습니다.
            </li>
            <li>
              이용자가 AI 응답을 기반으로 내린 판단에 대한 책임은 이용자에게
              있습니다.
            </li>
            <li>
              서비스는 천재지변, 시스템 장애 등 불가항력으로 인한 서비스 중단에
              대해 책임을 지지 않습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">제5조 (지적재산권)</h2>
          <p>
            서비스에서 제공하는 보험 약관 원문의 저작권은 각 보험사에 있으며,
            서비스는 공시된 약관 정보를 검색 및 질의응답 목적으로 활용합니다.
          </p>
        </section>

        <p className="text-xs text-muted-foreground pt-4 border-t">
          시행일: 2025년 1월 1일
        </p>
      </main>
    </div>
  );
}
