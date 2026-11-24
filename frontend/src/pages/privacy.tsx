import { ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router';

export default function PrivacyPage() {
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
        <h1 className="text-base font-semibold">개인정보처리방침</h1>
      </header>

      <main className="mx-auto max-w-2xl px-4 py-6 text-sm leading-relaxed text-foreground space-y-6">
        <p>
          Insurance AI(이하 &quot;회사&quot;)는 개인정보보호법 등 관련 법령에
          따라 이용자의 개인정보를 보호하고, 이와 관련한 고충을 신속하고
          원활하게 처리할 수 있도록 다음과 같이 개인정보처리방침을
          수립·공개합니다.
        </p>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            1. 수집하는 개인정보 항목 및 수집 방법
          </h2>
          <h3 className="font-semibold mb-2">가. 수집 항목</h3>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-xs">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-2 pr-3 font-semibold">구분</th>
                  <th className="text-left py-2 pr-3 font-semibold">항목</th>
                  <th className="text-left py-2 font-semibold">필수/선택</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                <tr>
                  <td className="py-2 pr-3 align-top">카카오 로그인</td>
                  <td className="py-2 pr-3">카카오 계정 식별자(ID), 닉네임</td>
                  <td className="py-2">필수</td>
                </tr>
                <tr>
                  <td className="py-2 pr-3 align-top">카카오 로그인</td>
                  <td className="py-2 pr-3">이메일 주소</td>
                  <td className="py-2">선택</td>
                </tr>
                <tr>
                  <td className="py-2 pr-3 align-top">서비스 이용</td>
                  <td className="py-2 pr-3">AI 질의응답 대화 이력, 검색 기록</td>
                  <td className="py-2">자동 수집</td>
                </tr>
                <tr>
                  <td className="py-2 pr-3 align-top">자동 생성</td>
                  <td className="py-2 pr-3">
                    접속 IP, 접속 일시, 브라우저 유형, 기기 정보
                  </td>
                  <td className="py-2">자동 수집</td>
                </tr>
              </tbody>
            </table>
          </div>

          <h3 className="font-semibold mt-4 mb-2">나. 수집 방법</h3>
          <ul className="list-disc pl-5 space-y-1">
            <li>카카오 OAuth 로그인 연동을 통한 수집</li>
            <li>서비스 이용 과정에서 자동으로 생성·수집</li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            2. 개인정보의 수집 및 이용 목적
          </h2>
          <ul className="list-disc pl-5 space-y-1">
            <li>회원 식별 및 가입 의사 확인, 서비스 이용 관리</li>
            <li>AI 질의응답 대화 이력 관리 및 맞춤형 서비스 제공</li>
            <li>서비스 개선, 신규 서비스 개발 및 통계 분석</li>
            <li>서비스 이용에 관한 공지사항 전달</li>
            <li>부정 이용 방지 및 서비스 안정성 확보</li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            3. 개인정보의 보유 및 이용 기간
          </h2>
          <p className="mb-2">
            회사는 원칙적으로 개인정보 수집·이용 목적이 달성된 후에는 해당
            정보를 지체 없이 파기합니다. 회원 탈퇴 시 즉시 파기하며, 관계
            법령에 따라 보존이 필요한 경우 해당 기간 동안 별도 보관합니다.
          </p>

          <h3 className="font-semibold mb-2">
            가. 회사 내부 방침에 의한 보관
          </h3>
          <ul className="list-disc pl-5 space-y-1">
            <li>서비스 이용 기록(대화 이력, 검색 기록): 탈퇴 후 3개월</li>
            <li>로그인 기록: 3개월</li>
          </ul>

          <h3 className="font-semibold mt-3 mb-2">
            나. 관련 법령에 의한 보관
          </h3>
          <ul className="list-disc pl-5 space-y-1">
            <li>
              계약 또는 청약 철회 등에 관한 기록: 5년 (전자상거래 등에서의
              소비자보호에 관한 법률)
            </li>
            <li>
              대금 결제 및 재화 등의 공급에 관한 기록: 5년 (전자상거래
              등에서의 소비자보호에 관한 법률)
            </li>
            <li>
              소비자의 불만 또는 분쟁 처리에 관한 기록: 3년 (전자상거래
              등에서의 소비자보호에 관한 법률)
            </li>
            <li>
              웹사이트 방문 기록: 3개월 (통신비밀보호법)
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            4. 개인정보의 제3자 제공
          </h2>
          <p>
            회사는 이용자의 개인정보를 제2조에서 명시한 범위 내에서만
            처리하며, 이용자의 동의 없이 제3자에게 제공하지 않습니다. 다만,
            다음의 경우에는 예외로 합니다.
          </p>
          <ul className="list-disc pl-5 mt-2 space-y-1">
            <li>이용자가 사전에 동의한 경우</li>
            <li>
              법령에 특별한 규정이 있거나 법령상 의무를 준수하기 위해
              불가피한 경우
            </li>
            <li>
              이용자 또는 그 법정대리인이 의사표시를 할 수 없는 상태에
              있거나 주소불명 등으로 사전 동의를 받을 수 없는 경우로서 명백히
              이용자 또는 제3자의 급박한 생명, 신체, 재산의 이익을 위해
              필요하다고 인정되는 경우
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            5. 개인정보 처리위탁
          </h2>
          <p className="mb-2">
            회사는 원활한 서비스 제공을 위해 다음과 같이 개인정보 처리를
            위탁하고 있습니다.
          </p>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-xs">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-2 pr-3 font-semibold">
                    수탁업체
                  </th>
                  <th className="text-left py-2 pr-3 font-semibold">
                    위탁 업무 내용
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y">
                <tr>
                  <td className="py-2 pr-3">
                    Amazon Web Services, Inc.
                  </td>
                  <td className="py-2">
                    클라우드 서버 운영 및 데이터 보관
                  </td>
                </tr>
                <tr>
                  <td className="py-2 pr-3">Anthropic, PBC</td>
                  <td className="py-2">AI 질의응답 처리 (API 연동)</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            위탁 계약 시 개인정보가 안전하게 관리될 수 있도록 필요한 사항을
            규정하고 있으며, 수탁업체가 개인정보를 안전하게 처리하는지
            감독합니다.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            6. 개인정보의 국외 이전
          </h2>
          <p className="mb-2">
            회사는 AI 질의응답 서비스 제공을 위해 이용자의 질문 내용을 아래와
            같이 국외로 이전합니다.
          </p>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-xs">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-2 pr-3 font-semibold">항목</th>
                  <th className="text-left py-2 font-semibold">내용</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                <tr>
                  <td className="py-2 pr-3 font-medium">이전받는 자</td>
                  <td className="py-2">Anthropic, PBC</td>
                </tr>
                <tr>
                  <td className="py-2 pr-3 font-medium">이전되는 국가</td>
                  <td className="py-2">미국</td>
                </tr>
                <tr>
                  <td className="py-2 pr-3 font-medium">이전 목적</td>
                  <td className="py-2">
                    AI 기반 보험 약관 질의응답 처리
                  </td>
                </tr>
                <tr>
                  <td className="py-2 pr-3 font-medium">이전 항목</td>
                  <td className="py-2">이용자의 질문 내용</td>
                </tr>
                <tr>
                  <td className="py-2 pr-3 font-medium">이전 방법</td>
                  <td className="py-2">API 호출을 통한 네트워크 전송</td>
                </tr>
                <tr>
                  <td className="py-2 pr-3 font-medium">보유 기간</td>
                  <td className="py-2">
                    API 처리 완료 후 Anthropic 정책에 따라 처리
                    (AI 모델 학습에는 미사용)
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            이용자는 AI 질의응답 기능 이용을 거부함으로써 국외 이전에 대한
            동의를 거부할 수 있으며, 이 경우 AI 질의응답 기능 이용이
            제한됩니다. 검색 등 AI 질의응답 외 서비스는 정상 이용 가능합니다.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            7. 개인정보의 파기 절차 및 방법
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              <span className="font-semibold">파기 절차</span>: 수집·이용
              목적이 달성되거나 보유 기간이 경과한 개인정보는 별도의 DB로
              옮겨져(종이의 경우 별도의 서류함) 내부 방침 및 관련 법령에 따라
              일정 기간 보관 후 파기됩니다.
            </li>
            <li>
              <span className="font-semibold">파기 방법</span>: 전자적 파일
              형태의 정보는 기록을 재생할 수 없는 기술적 방법을 사용하여
              삭제하며, 종이에 출력된 개인정보는 분쇄기로 분쇄하거나
              소각합니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            8. 이용자의 권리·의무 및 행사 방법
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              이용자는 언제든지 자신의 개인정보에 대해 열람, 정정, 삭제,
              처리 정지를 요구할 수 있습니다.
            </li>
            <li>
              권리 행사는 서비스 내 프로필 페이지에서 직접 수행하거나, 회사에
              서면, 이메일 등으로 요청할 수 있으며, 회사는 이에 대해 지체 없이
              조치합니다.
            </li>
            <li>
              회원 탈퇴를 통해 개인정보의 수집·이용에 대한 동의를 철회할 수
              있습니다.
            </li>
            <li>
              이용자가 개인정보의 오류에 대한 정정을 요청한 경우, 정정이
              완료되기 전까지 해당 개인정보를 이용하거나 제공하지 않습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            9. 자동화된 의사결정에 관한 사항
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              회사는 AI 모델을 활용하여 보험 약관에 대한 자동화된 응답을
              생성합니다. 이는 이용자의 질문에 대한 정보 제공 목적으로만
              활용되며, 이용자의 권리·의무에 중대한 영향을 미치는 자동화된
              의사결정에는 해당하지 않습니다.
            </li>
            <li>
              개인정보보호법 제37조의2에 따라, 이용자는 자동화된 결정에 대해
              다음의 권리를 행사할 수 있습니다.
              <ul className="list-disc pl-5 mt-1 space-y-1">
                <li>자동화된 결정을 거부할 권리</li>
                <li>자동화된 결정에 대한 설명을 요구할 권리</li>
                <li>
                  인적 개입(사람에 의한 재검토)을 요구할 권리
                </li>
              </ul>
            </li>
            <li>
              이용자의 대화 내용은 AI 모델의 학습 데이터로 사용되지 않습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            10. 쿠키의 설치·운영 및 거부에 관한 사항
          </h2>
          <ol className="list-decimal pl-5 space-y-2">
            <li>
              회사는 이용자의 인증 상태 유지를 위해 쿠키(Cookie)를
              사용합니다. 쿠키는 웹사이트 서버가 이용자의 브라우저에 전송하는
              소량의 데이터입니다.
            </li>
            <li>
              <span className="font-semibold">쿠키 사용 목적</span>: 로그인
              인증 토큰 관리, 서비스 이용 환경 유지
            </li>
            <li>
              <span className="font-semibold">쿠키 거부 방법</span>:
              이용자는 브라우저 설정을 통해 쿠키 저장을 거부할 수 있습니다.
              다만, 쿠키를 거부할 경우 로그인이 필요한 서비스 이용에 제한이
              있을 수 있습니다.
            </li>
          </ol>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            11. 개인정보 보호를 위한 기술적·관리적 조치
          </h2>
          <h3 className="font-semibold mb-2">가. 기술적 조치</h3>
          <ul className="list-disc pl-5 space-y-1">
            <li>JWT 기반 인증 토큰 암호화 및 안전한 관리</li>
            <li>HTTPS(TLS) 통신 암호화</li>
            <li>개인정보 접근 통제 및 접근 권한 최소화</li>
            <li>데이터베이스 접근 제한 및 보안 설정</li>
          </ul>
          <h3 className="font-semibold mt-3 mb-2">나. 관리적 조치</h3>
          <ul className="list-disc pl-5 space-y-1">
            <li>개인정보 취급자 최소화</li>
            <li>개인정보 처리 관련 내부 관리 계획 수립·시행</li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            12. 만 14세 미만 아동의 개인정보 처리
          </h2>
          <p>
            회사는 만 14세 미만 아동의 회원가입을 제한하고 있습니다.
            카카오 로그인을 통해 서비스에 가입하므로, 카카오 계정의 연령
            제한 정책에 따릅니다. 만 14세 미만 아동의 개인정보를 수집하는
            경우에는 법정대리인의 동의를 받아 처리합니다.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            13. 개인정보 보호책임자
          </h2>
          <p className="mb-2">
            회사는 이용자의 개인정보 관련 문의사항 및 불만 처리를 위해 아래와
            같이 개인정보 보호책임자를 지정하고 있습니다.
          </p>
          <ul className="list-none space-y-1 bg-muted/50 rounded-lg p-3">
            <li>
              <span className="font-semibold">연락처</span>: 서비스 운영 종료로 문의를 받지 않습니다
            </li>
          </ul>
          <p className="mt-2 text-xs text-muted-foreground">
            서비스 이용 중 발생하는 모든 개인정보 관련 문의, 불만, 피해 구제
            등에 관한 사항을 위 개인정보 보호책임자에게 문의하실 수 있습니다.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            14. 개인정보 침해 구제 기관 안내
          </h2>
          <p className="mb-2">
            이용자의 개인정보 침해에 대한 신고나 상담이 필요한 경우 아래 기관에
            문의하실 수 있습니다.
          </p>
          <ul className="list-disc pl-5 space-y-2">
            <li>
              개인정보침해 신고센터 (한국인터넷진흥원)
              <br />
              <span className="text-muted-foreground">
                전화: (국번없이) 118 | 웹사이트: privacy.kisa.or.kr
              </span>
            </li>
            <li>
              개인정보 분쟁조정위원회
              <br />
              <span className="text-muted-foreground">
                전화: (국번없이) 1833-6972 | 웹사이트: www.kopico.go.kr
              </span>
            </li>
            <li>
              대검찰청 사이버수사과
              <br />
              <span className="text-muted-foreground">
                전화: (국번없이) 1301 | 웹사이트: www.spo.go.kr
              </span>
            </li>
            <li>
              경찰청 사이버수사국
              <br />
              <span className="text-muted-foreground">
                전화: (국번없이) 182 | 웹사이트: ecrm.police.go.kr
              </span>
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">
            15. 개인정보처리방침의 변경
          </h2>
          <p>
            본 개인정보처리방침은 법령, 정책 또는 서비스의 변경에 따라 내용이
            추가·삭제·수정될 수 있습니다. 변경 사항은 시행일 7일 전부터
            서비스 내 공지사항을 통해 고지합니다. 다만, 이용자의 권리에
            중요한 변경이 있을 경우 시행일 30일 전에 고지합니다.
          </p>
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
