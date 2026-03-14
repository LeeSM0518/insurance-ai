import { useState } from 'react';
import { FeedbackRequestCategoryEnum } from '@insurance-ai/api-client';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Button } from '@/components/ui/button';
import type { FeedbackCategory } from './use-feedback';

const CATEGORY_OPTIONS: { value: FeedbackCategory; label: string }[] = [
  { value: FeedbackRequestCategoryEnum.Inaccurate, label: '정보가 부정확해요' },
  { value: FeedbackRequestCategoryEnum.Misunderstood, label: '질문을 이해하지 못했어요' },
  { value: FeedbackRequestCategoryEnum.Insufficient, label: '정보가 부족해요' },
  { value: FeedbackRequestCategoryEnum.WrongSource, label: '출처가 잘못되었어요' },
  { value: FeedbackRequestCategoryEnum.Other, label: '기타' },
];

const MAX_COMMENT_LENGTH = 500;

interface FeedbackCategoryPopoverProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (category: FeedbackCategory, comment?: string) => void;
  isLoading: boolean;
  children: React.ReactNode;
}

export function FeedbackCategoryPopover({
  open,
  onOpenChange,
  onSubmit,
  isLoading,
  children,
}: FeedbackCategoryPopoverProps) {
  const [selectedCategory, setSelectedCategory] = useState<FeedbackCategory | null>(null);
  const [comment, setComment] = useState('');

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) {
      // Popover 닫힐 때 선택 초기화
      setSelectedCategory(null);
      setComment('');
    }
    onOpenChange(nextOpen);
  };

  const handleSubmit = () => {
    if (!selectedCategory) return;
    const trimmedComment = comment.trim();
    onSubmit(
      selectedCategory,
      selectedCategory === FeedbackRequestCategoryEnum.Other && trimmedComment
        ? trimmedComment
        : undefined,
    );
    setSelectedCategory(null);
    setComment('');
  };

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger render={children as React.ReactElement} />
      <PopoverContent side="top" align="start" className="w-72 p-3">
        <p className="text-sm font-medium mb-2">어떤 점이 불만족스러우셨나요?</p>
        <div className="flex flex-col gap-1">
          {CATEGORY_OPTIONS.map((option) => (
            <label
              key={option.value}
              className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm cursor-pointer hover:bg-muted transition-colors"
            >
              <input
                type="radio"
                name="feedback-category"
                value={option.value}
                checked={selectedCategory === option.value}
                onChange={() => setSelectedCategory(option.value)}
                className="accent-primary"
              />
              {option.label}
            </label>
          ))}
        </div>
        {selectedCategory === FeedbackRequestCategoryEnum.Other && (
          <div className="mt-2">
            <textarea
              value={comment}
              onChange={(e) => setComment(e.target.value.slice(0, MAX_COMMENT_LENGTH))}
              placeholder="의견을 입력해주세요..."
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring resize-none"
              rows={3}
            />
            <p className="text-xs text-muted-foreground text-right mt-0.5">
              {comment.length}/{MAX_COMMENT_LENGTH}
            </p>
          </div>
        )}
        <Button
          size="sm"
          className="w-full mt-2"
          disabled={!selectedCategory || isLoading}
          onClick={handleSubmit}
        >
          제출
        </Button>
      </PopoverContent>
    </Popover>
  );
}
