import { ClipboardList, BookOpen, AlertTriangle, FileText } from 'lucide-react';

interface ChatWelcomeProps {
  onSelectQuestion: (question: string) => void;
}

const WELCOME_QUESTIONS = [
  {
    icon: ClipboardList,
    label: '보장 비교',
    question: '뇌혈관질환 진단비 보장이 있는 상품을 비교해줘',
  },
  {
    icon: BookOpen,
    label: '약관 해석',
    question: '실손보험에서 보장하는 의료비 범위가 어떻게 돼?',
  },
  {
    icon: AlertTriangle,
    label: '면책·감액',
    question: '암보험에서 유사암이 제외되는 경우는?',
  },
  {
    icon: FileText,
    label: '보험금 지급',
    question: '종신보험 사망보험금 지급 조건이 뭐야?',
  },
];

export function ChatWelcome({ onSelectQuestion }: ChatWelcomeProps) {
  return (
    <div className="flex flex-col items-center justify-center h-full py-12 px-4">
      <div className="flex flex-col items-center gap-2 mb-8">
        <div className="size-12 rounded-full bg-primary/10 flex items-center justify-center mb-2">
          <BookOpen className="size-6 text-primary" />
        </div>
        <h2 className="text-lg font-semibold text-foreground">
          보험 약관, 무엇이든 물어보세요
        </h2>
        <p className="text-sm text-muted-foreground">
          약관 내용을 AI가 쉽게 설명해드립니다
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-w-lg w-full">
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
    </div>
  );
}
