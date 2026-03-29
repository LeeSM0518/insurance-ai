import { useNavigate } from 'react-router';
import { Swords, FileCheck, MessageSquareText, AlertTriangle, RefreshCw, FileText, BookOpen, BarChart3 } from 'lucide-react';

interface ChatWelcomeProps {
  onSelectQuestion: (question: string) => void;
}

const WELCOME_QUESTIONS = [
  {
    icon: Swords,
    label: '상품 비교',
    question: '삼성생명과 한화생명의 암보험 보장 범위와 면책기간을 비교해줘',
  },
  {
    icon: FileCheck,
    label: '청구 가이드',
    question: '암보험 보험금 청구할 때 필요한 서류와 절차를 알려줘',
  },
  {
    icon: MessageSquareText,
    label: '고객 설명',
    question: '뇌혈관질환 진단비 특약의 보장 내용과 면책사항을 고객에게 설명할 수 있게 정리해줘',
  },
  {
    icon: AlertTriangle,
    label: '면책·감액',
    question: '암보험에서 유사암이 제외되는 경우와 감액기간은 어떻게 돼?',
  },
  {
    icon: RefreshCw,
    label: '갱신 비교',
    question: '갱신형과 비갱신형 암보험의 약관상 차이점을 비교해줘',
  },
  {
    icon: FileText,
    label: '치트시트',
    question: '암보험 상담 준비 치트시트를 만들어주세요',
  },
];

const QUICK_LINKS = [
  { icon: BarChart3, label: '보험 비교 분석', path: '/compare' },
  { icon: FileText, label: '치트시트 생성', path: '/cheatsheet' },
];

export function ChatWelcome({ onSelectQuestion }: ChatWelcomeProps) {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col items-center justify-center h-full py-12 px-4">
      <div className="flex flex-col items-center gap-2 mb-8">
        <div className="size-12 rounded-full bg-primary/10 flex items-center justify-center mb-2">
          <BookOpen className="size-6 text-primary" />
        </div>
        <h2 className="text-lg font-semibold text-foreground">
          AI 영업 비서에게 물어보세요
        </h2>
        <p className="text-sm text-muted-foreground">
          약관 비교, 영업 논리, 청구 가이드까지 — 상담 준비를 도와드립니다
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-w-xl w-full">
        {WELCOME_QUESTIONS.map(({ icon: Icon, label, question }) => (
          <button
            key={label}
            className="flex items-start gap-3 rounded-lg border p-4 text-left hover:bg-muted transition-colors"
            onClick={() => onSelectQuestion(question)}
          >
            <Icon className="size-5 text-muted-foreground flex-shrink-0 mt-0.5" />
            <div className="min-w-0">
              <div className="text-sm font-medium text-foreground">{label}</div>
              <div className="text-xs text-muted-foreground mt-1 line-clamp-2">
                {question}
              </div>
            </div>
          </button>
        ))}
      </div>

      {/* 기능 바로가기 */}
      <div className="flex gap-2 mt-6">
        {QUICK_LINKS.map(({ icon: Icon, label, path }) => (
          <button
            key={path}
            className="flex items-center gap-1.5 px-4 py-2 rounded-full border text-sm text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
            onClick={() => navigate(path)}
          >
            <Icon className="size-4" />
            {label}
          </button>
        ))}
      </div>
    </div>
  );
}
