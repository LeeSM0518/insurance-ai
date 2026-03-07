interface SuggestedQuestionsProps {
  questions: string[];
  onSelect: (question: string) => void;
}

export function SuggestedQuestions({ questions, onSelect }: SuggestedQuestionsProps) {
  if (questions.length === 0) return null;

  return (
    <div className="flex flex-wrap gap-2 mt-3">
      {questions.map((question) => (
        <button
          key={question}
          className="rounded-full border bg-background px-3 py-1.5 text-xs text-foreground hover:bg-muted transition-colors"
          onClick={() => onSelect(question)}
        >
          {question}
        </button>
      ))}
    </div>
  );
}
