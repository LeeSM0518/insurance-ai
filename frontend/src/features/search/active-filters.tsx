import { X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import type { SearchFilters, InsuranceCompany, ContentType } from '@/types/search';
import { CONTENT_TYPE_LABELS, INSURANCE_TYPE_LABELS } from '@/types/search';

interface ActiveFiltersProps {
  filters: SearchFilters;
  companies: InsuranceCompany[];
  onRemoveCompany: (id: string) => void;
  onRemoveInsuranceType: () => void;
  onRemoveCategory: (code: string) => void;
  onRemoveContentType: (type: ContentType) => void;
}

export function ActiveFilters({
  filters,
  companies,
  onRemoveCompany,
  onRemoveInsuranceType,
  onRemoveCategory,
  onRemoveContentType,
}: ActiveFiltersProps) {
  const hasActiveFilters =
    filters.insuranceCompanyIds.length > 0 ||
    filters.insuranceType !== null ||
    filters.categories.length > 0 ||
    filters.contentTypes.length > 0;

  if (!hasActiveFilters) return null;

  return (
    <div className="flex gap-1.5 flex-wrap" aria-label="적용중인 필터">
      {filters.insuranceType && (
        <Badge variant="default" className="gap-1 cursor-pointer" onClick={onRemoveInsuranceType}>
          {INSURANCE_TYPE_LABELS[filters.insuranceType]}
          <X className="size-3" />
        </Badge>
      )}
      {filters.insuranceCompanyIds.map((id) => {
        const company = companies.find((c) => c.id === id);
        if (!company) return null;
        return (
          <Badge key={id} variant="default" className="gap-1 cursor-pointer" onClick={() => onRemoveCompany(id)}>
            {company.name}
            <X className="size-3" />
          </Badge>
        );
      })}
      {filters.categories.map((code) => (
        <Badge key={code} variant="default" className="gap-1 cursor-pointer" onClick={() => onRemoveCategory(code)}>
          {code}
          <X className="size-3" />
        </Badge>
      ))}
      {filters.contentTypes.map((type) => (
        <Badge
          key={type}
          variant="default"
          className="gap-1 cursor-pointer"
          onClick={() => onRemoveContentType(type)}
        >
          {CONTENT_TYPE_LABELS[type]}
          <X className="size-3" />
        </Badge>
      ))}
    </div>
  );
}
