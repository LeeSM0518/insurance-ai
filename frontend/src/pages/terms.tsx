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
            본 약관은 Insurance AI(이하 &quot;회사&quot;)가 제공하는 보험 약관
            검색 및 AI 질의응답 서비스(이하 &quot;서비스&quot;)의 이용과 관련하여
            회사와 이용자 간의 권리, 의무 및 책임사항, 기타 필요한 사항을
            규정함을 목적으로 합니다.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">제2조 (정의)</h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              &quot;서비스&quot;란 회사가 제공하는 보험 약관 검색, AI 기반
              질의응답, 보험 상품 정보 조회 등 관련 일체의 서비스를 의미합니다.
            </li>
            <li>
              &quot;이용자&quot;란 본 약관에 동의하고 회사가 제공하는 서비스를
              이용하는 자를 의미합니다.
            </li>
            <li>
              &quot;AI 응답&quot;이란 인공지능 모델이 보험 약관 데이터를
              기반으로 생성한 답변을 의미하며, 이는 자동화된 알고리즘에 의해
              생성됩니다.
            </li>
            <li>
              &quot;계정&quot;이란 이용자가 서비스에 로그인하기 위해 사용하는
              카카오 계정 기반의 인증 정보를 의미합니다.
            </li>
            <li>
              &quot;콘텐츠&quot;란 서비스 내에서 이용자가 입력한 질문, AI가
              생성한 응답, 검색 결과 등 서비스 이용 과정에서 생성되는 모든
              정보를 의미합니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            제3조 (약관의 효력 및 변경)
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              본 약관은 서비스 화면에 게시하거나 기타의 방법으로 이용자에게
              공지함으로써 효력이 발생합니다.
            </li>
            <li>
              회사는 관련 법령을 위배하지 않는 범위에서 본 약관을 개정할 수
              있으며, 약관을 개정할 경우 적용일자 및 개정사유를 명시하여
              적용일자 7일 전부터 서비스 내에 공지합니다. 다만, 이용자에게
              불리한 약관 변경의 경우에는 적용일자 30일 전부터 공지합니다.
            </li>
            <li>
              이용자가 변경된 약관에 동의하지 않는 경우 서비스 이용을 중단하고
              탈퇴할 수 있습니다. 변경된 약관의 효력 발생일 이후에도 서비스를
              계속 이용하는 경우 약관 변경에 동의한 것으로 간주합니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            제4조 (이용 계약의 체결)
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              이용 계약은 이용자가 본 약관에 동의하고, 카카오 계정을 통해
              로그인을 완료함으로써 체결됩니다.
            </li>
            <li>
              회사는 다음 각 호에 해당하는 경우 이용 신청을 승낙하지 않거나
              사후에 이용 계약을 해지할 수 있습니다.
              <ul className="list-disc pl-5 mt-1 space-y-1">
                <li>타인의 정보를 도용한 경우</li>
                <li>
                  서비스 운영을 방해하거나 방해할 우려가 있는 행위를 한 경우
                </li>
                <li>기타 관련 법령이나 회사가 정한 이용 기준에 위반한 경우</li>
              </ul>
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            제5조 (서비스의 내용)
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              회사는 다음의 서비스를 제공합니다.
              <ul className="list-disc pl-5 mt-1 space-y-1">
                <li>보험사별 약관 검색 서비스</li>
                <li>AI 기반 보험 약관 질의응답 서비스</li>
                <li>보험 상품 및 약관 상세 정보 조회 서비스</li>
                <li>기타 회사가 추가로 개발하거나 제휴를 통해 제공하는 서비스</li>
              </ul>
            </li>
            <li>
              서비스는 연중무휴, 1일 24시간 제공을 원칙으로 합니다. 다만,
              시스템 점검 등 회사의 업무상 또는 기술상의 이유로 서비스가 일시
              중단될 수 있습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            제6조 (AI 기반 서비스 고지)
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              본 서비스는 인공지능(AI) 기술에 기반하여 운용됩니다.
              인공지능기본법에 따라 이용자에게 이를 사전에 고지합니다.
            </li>
            <li>
              AI 질의응답 기능의 응답은 생성형 AI를 통해 생성된 결과물이며,
              이용자가 이를 명확히 인식할 수 있도록 서비스 내에 표시합니다.
            </li>
            <li>
              이용자의 질문 내용은 AI 응답 생성을 위해 외부 AI 모델
              제공사(Anthropic)의 API로 전송되며, AI 모델의 학습 데이터로는
              사용되지 않습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            제7조 (AI 응답의 특성 및 한계)
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              AI 응답은 인공지능 모델에 의해 자동으로 생성되며, 보험 약관
              원문을 기반으로 하되 완벽한 정확성을 보장하지 않습니다. 서비스는
              &quot;있는 그대로(AS IS)&quot; 제공됩니다.
            </li>
            <li>
              AI 응답은 법률적, 재무적, 의료적 조언을 구성하지 않으며, 전문
              상담을 대체할 수 없습니다. 이용자는 중요한 의사결정 시 반드시
              해당 분야 전문가와 상담하여야 합니다.
            </li>
            <li>
              AI 모델의 특성상 사실과 다른 정보(할루시네이션)가 포함될 수
              있으므로, 이용자는 AI 응답의 내용을 반드시 원본 약관과 대조하여
              확인해야 합니다.
            </li>
            <li>
              AI의 보험 약관 해석은 참고 목적으로만 제공되며, 법적 효력이 있는
              공식 해석이 아닙니다. AI 응답을 근거로 한 보험 계약의 체결,
              변경, 해지 등에 대해 회사는 책임지지 않습니다.
            </li>
            <li>
              회사는 AI 응답의 품질 향상을 위해 지속적으로 노력하나, AI
              기술의 내재적 한계로 인해 응답의 완전성, 정확성, 최신성을
              보증하지 않습니다.
            </li>
            <li>
              서비스에서 제공하는 AI 응답에는 출처(근거 약관 조항)가 함께
              표시되며, 이용자는 해당 출처를 통해 원문을 확인할 수 있습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            제8조 (이용자의 의무)
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              이용자는 다음 각 호의 행위를 하여서는 안 됩니다.
              <ul className="list-disc pl-5 mt-1 space-y-1">
                <li>타인의 계정을 무단으로 사용하는 행위</li>
                <li>서비스를 통해 얻은 정보를 회사의 사전 동의 없이 상업적으로 이용하거나 재판매하는 행위</li>
                <li>서비스의 안정적 운영을 방해하는 행위 (비정상적 대량 요청, 자동화 스크립트 사용 등)</li>
                <li>서비스를 이용하여 법령 또는 공서양속에 반하는 행위</li>
                <li>AI 응답을 허위 또는 오해의 소지가 있는 방식으로 제3자에게 전달하는 행위</li>
                <li>회사 또는 제3자의 지적재산권을 침해하는 행위</li>
                <li>AI 질의응답에 고객의 주민등록번호, 계좌번호, 카드번호 등 민감한 개인정보를 입력하는 행위</li>
              </ul>
            </li>
            <li>
              이용자는 AI 응답을 고객에게 전달하기 전에 원본 약관과 대조하여
              정확성을 검증할 의무가 있습니다.
            </li>
            <li>
              이용자는 보험업법, 금융소비자보호법 등 관련 법규를 준수하여야
              하며, 서비스 이용으로 인한 법규 위반의 책임은 이용자에게
              있습니다.
            </li>
            <li>
              이용자는 관련 법령, 본 약관의 규정, 서비스 이용 안내 및 주의사항
              등을 준수하여야 합니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            제9조 (서비스의 변경 및 중단)
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              회사는 운영상, 기술상의 필요에 따라 서비스의 전부 또는 일부를
              변경할 수 있으며, 변경 시 사전에 서비스 내에 공지합니다.
            </li>
            <li>
              다음 각 호의 경우 서비스의 전부 또는 일부를 제한하거나 중단할 수
              있습니다.
              <ul className="list-disc pl-5 mt-1 space-y-1">
                <li>시스템 정기 점검, 증설, 교체 등의 경우</li>
                <li>천재지변, 전쟁, 정전, 설비 장애 등 불가항력적 사유가 발생한 경우</li>
                <li>AI 모델 제공사(Anthropic 등)의 서비스 중단 또는 장애가 발생한 경우</li>
                <li>기타 회사의 합리적 판단에 의해 서비스 제공이 어려운 경우</li>
              </ul>
            </li>
            <li>
              회사는 서비스 중단의 경우 가능한 한 사전에 공지하며, 예측할 수
              없는 사유로 인한 중단의 경우 사후에 공지할 수 있습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            제10조 (계약 해지 및 이용 제한)
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              이용자는 언제든지 서비스 내 프로필 페이지를 통해 회원 탈퇴를
              신청할 수 있으며, 회사는 즉시 탈퇴를 처리합니다.
            </li>
            <li>
              회사는 이용자가 본 약관을 위반한 경우, 사전 통지 후 서비스 이용을
              제한하거나 이용 계약을 해지할 수 있습니다. 다만, 긴급한 경우
              사후에 통지할 수 있습니다.
            </li>
            <li>
              회원 탈퇴 시 이용자의 개인정보 및 대화 이력은
              개인정보처리방침에 따라 처리됩니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">제11조 (지적재산권)</h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              서비스에서 제공하는 보험 약관 원문의 저작권은 각 보험사에 있으며,
              회사는 공시된 약관 정보를 검색 및 질의응답 목적으로 활용합니다.
            </li>
            <li>
              서비스의 디자인, 로고, 소프트웨어, AI 모델 구성 등에 대한
              지적재산권은 회사에 귀속됩니다.
            </li>
            <li>
              이용자는 서비스를 통해 얻은 정보를 회사의 사전 서면 동의 없이
              복제, 배포, 방송 또는 기타 방법으로 이용하거나 제3자에게 제공할
              수 없습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">제12조 (손해배상)</h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              회사는 무료로 제공하는 서비스의 이용과 관련하여 개인정보보호법 등
              관련 법령에 특별한 규정이 없는 한, 이용자에게 발생한 손해에 대해
              책임을 지지 않습니다.
            </li>
            <li>
              이용자가 본 약관을 위반하여 회사에 손해를 발생시킨 경우, 해당
              이용자는 회사에 발생한 손해를 배상하여야 합니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">제13조 (면책사항)</h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              회사는 AI 응답의 완전성, 정확성, 적시성, 적합성을 보장하지
              않으며, AI 응답을 기반으로 이용자가 내린 판단에 대한 책임은
              이용자에게 있습니다.
            </li>
            <li>
              회사는 천재지변, 전쟁, 기간통신사업자의 서비스 중지, 해결이
              곤란한 기술적 결함 등 불가항력으로 인한 서비스 중단에 대해
              책임을 지지 않습니다.
            </li>
            <li>
              회사는 이용자의 귀책 사유로 인한 서비스 이용 장애에 대해 책임을
              지지 않습니다.
            </li>
            <li>
              회사는 이용자가 서비스를 통해 기대하는 수익을 얻지 못하거나
              서비스 자료에 대한 취사선택 또는 이용으로 발생하는 손해에 대해
              책임을 지지 않습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            제14조 (분쟁 해결 및 관할)
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              본 약관과 관련하여 분쟁이 발생한 경우, 회사와 이용자는 분쟁의
              해결을 위해 성실히 협의합니다.
            </li>
            <li>
              협의에도 불구하고 분쟁이 해결되지 않는 경우, 관련 법령에 따른
              관할 법원에 소를 제기할 수 있습니다.
            </li>
            <li>
              본 약관에 명시되지 않은 사항은 관련 법령 및 상관례에 따릅니다.
            </li>
          </ol>
        </section>

        <section className="space-y-1">
          <p className="text-xs text-muted-foreground pt-4 border-t">
            공고일: 2026년 3월 11일
          </p>
          <p className="text-xs text-muted-foreground">
            시행일: 2026년 3월 11일
          </p>
        </section>
      </main>
    </div>
  );
}
