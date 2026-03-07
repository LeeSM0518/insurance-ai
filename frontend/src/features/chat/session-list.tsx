import { SessionItem } from './session-item';
import type { ChatSession } from '@/types/chat';

interface SessionListProps {
  sessions: ChatSession[];
  onDelete: (id: string) => void;
}

export function SessionList({ sessions, onDelete }: SessionListProps) {
  return (
    <div className="divide-y">
      {sessions.map((session) => (
        <SessionItem key={session.id} session={session} onDelete={onDelete} />
      ))}
    </div>
  );
}
