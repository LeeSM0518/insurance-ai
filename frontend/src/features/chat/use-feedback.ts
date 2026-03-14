import { useState, useCallback } from 'react';
import { toast } from 'sonner';
import { getAIApi } from '@/lib/api';
import {
  FeedbackRequestCategoryEnum,
  FeedbackRequestFeedbackTypeEnum,
} from '@insurance-ai/api-client';

export type FeedbackCategory = FeedbackRequestCategoryEnum;

type FeedbackType = 'POSITIVE' | 'NEGATIVE' | null;

interface UseFeedbackOptions {
  messageId: string;
  initialFeedback?: {
    feedbackType: 'POSITIVE' | 'NEGATIVE';
    category?: FeedbackCategory;
    comment?: string;
  } | null;
}

interface UseFeedbackReturn {
  feedbackType: FeedbackType;
  category: FeedbackCategory | null;
  isLoading: boolean;
  submitPositive: () => Promise<void>;
  submitNegative: (category: FeedbackCategory, comment?: string) => Promise<void>;
  removeFeedback: () => Promise<void>;
}

export function useFeedback({ messageId, initialFeedback = null }: UseFeedbackOptions): UseFeedbackReturn {
  const [feedbackType, setFeedbackType] = useState<FeedbackType>(initialFeedback?.feedbackType ?? null);
  const [category, setCategory] = useState<FeedbackCategory | null>(
    (initialFeedback?.category as FeedbackCategory) ?? null,
  );
  const [isLoading, setIsLoading] = useState(false);

  const submitPositive = useCallback(async () => {
    // 이미 POSITIVE면 토글 해제
    if (feedbackType === 'POSITIVE') {
      const prev = { feedbackType, category };
      setFeedbackType(null);
      setCategory(null);
      setIsLoading(true);
      try {
        await getAIApi().deleteFeedback({ messageId });
      } catch {
        setFeedbackType(prev.feedbackType);
        setCategory(prev.category);
        toast.error('피드백 삭제에 실패했습니다');
      } finally {
        setIsLoading(false);
      }
      return;
    }

    // 낙관적 업데이트
    const prev = { feedbackType, category };
    setFeedbackType('POSITIVE');
    setCategory(null);
    setIsLoading(true);
    try {
      await getAIApi().submitFeedback({
        messageId,
        feedbackRequest: {
          feedbackType: FeedbackRequestFeedbackTypeEnum.Positive,
        },
      });
    } catch {
      setFeedbackType(prev.feedbackType);
      setCategory(prev.category);
      toast.error('피드백 저장에 실패했습니다');
    } finally {
      setIsLoading(false);
    }
  }, [feedbackType, category, messageId]);

  const submitNegative = useCallback(
    async (selectedCategory: FeedbackCategory, comment?: string) => {
      const prev = { feedbackType, category };
      setFeedbackType('NEGATIVE');
      setCategory(selectedCategory);
      setIsLoading(true);
      try {
        await getAIApi().submitFeedback({
          messageId,
          feedbackRequest: {
            feedbackType: FeedbackRequestFeedbackTypeEnum.Negative,
            category: selectedCategory,
            ...(selectedCategory === FeedbackRequestCategoryEnum.Other && comment ? { comment } : {}),
          },
        });
      } catch {
        setFeedbackType(prev.feedbackType);
        setCategory(prev.category);
        toast.error('피드백 저장에 실패했습니다');
      } finally {
        setIsLoading(false);
      }
    },
    [feedbackType, category, messageId],
  );

  const removeFeedback = useCallback(async () => {
    const prev = { feedbackType, category };
    setFeedbackType(null);
    setCategory(null);
    setIsLoading(true);
    try {
      await getAIApi().deleteFeedback({ messageId });
    } catch {
      setFeedbackType(prev.feedbackType);
      setCategory(prev.category);
      toast.error('피드백 삭제에 실패했습니다');
    } finally {
      setIsLoading(false);
    }
  }, [feedbackType, category, messageId]);

  return {
    feedbackType,
    category,
    isLoading,
    submitPositive,
    submitNegative,
    removeFeedback,
  };
}
