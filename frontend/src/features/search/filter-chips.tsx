import { Badge } from '@/components/ui/badge';
import { Accordion, AccordionItem, AccordionTrigger, AccordionContent } from '@/components/ui/accordion';
import type { SearchFilters, InsuranceCompany, Category, InsuranceType, ContentType } from '@/types/search';
import { CONTENT_TYPE_LABELS, INSURANCE_TYPE_LABELS } from '@/types/search';
import { X } from 'lucide-react';

interface FilterChipsProps {
  filters: SearchFilters;
  onFiltersChange: (filters: SearchFilters) => void;
  companies: InsuranceCompany[];
  categories: Category[];
  /** 제목 숨김 (검색 결과 페이지용) */
  compact?: boolean;
}

export function FilterChips({ filters, onFiltersChange, companies, categories, compact = false }: FilterChipsProps) {
  const toggleCompany = (id: string) => {
    const ids = filters.insuranceCompanyIds.includes(id)
      ? filters.insuranceCompanyIds.filter((c) => c !== id)
      : [...filters.insuranceCompanyIds, id];
    onFiltersChange({ ...filters, insuranceCompanyIds: ids });
  };

  const toggleInsuranceType = (type: InsuranceType) => {
    onFiltersChange({
      ...filters,
      insuranceType: filters.insuranceType === type ? null : type,
      // 생/손보 변경 시 카테고리 필터 초기화
      categories: filters.insuranceType === type ? filters.categories : [],
    });
  };

  const toggleCategory = (code: string) => {
    const cats = filters.categories.includes(code)
      ? filters.categories.filter((c) => c !== code)
      : [...filters.categories, code];
    onFiltersChange({ ...filters, categories: cats });
  };

  const toggleContentType = (type: ContentType) => {
    const types = filters.contentTypes.includes(type)
      ? filters.contentTypes.filter((t) => t !== type)
      : [...filters.contentTypes, type];
    onFiltersChange({ ...filters, contentTypes: types });
  };

  const clearAll = () => {
    onFiltersChange({
      insuranceCompanyIds: [],
      insuranceType: null,
      categories: [],
      contentTypes: [],
    });
  };

  // 생/손보 필터가 선택되면 해당 보험종류 카테고리만 표시
  const filteredCategories = filters.insuranceType
    ? categories.filter((c) => c.insuranceType === filters.insuranceType)
    : categories;

  // 선택 개수 계산
  const companyCount = filters.insuranceCompanyIds.length;
  const categoryCount = filters.categories.length;
  const contentTypeCount = filters.contentTypes.length;
  const hasActiveFilters = companyCount > 0 || filters.insuranceType !== null || categoryCount > 0 || contentTypeCount > 0;

  const triggerClass = compact ? 'py-1 text-xs' : 'py-1.5 text-sm';
  const contentClass = compact ? 'pb-1.5' : 'pb-1.5';

  return (
    <section aria-labelledby={compact ? undefined : 'filter-label'} className="space-y-2">
      {!compact && (
        <h2 id="filter-label" className="text-base font-medium text-muted-foreground">
          필터
        </h2>
      )}

      {/* Active Chips: 선택된 필터 요약 */}
      {hasActiveFilters && (
        <div className="flex gap-1.5 flex-wrap items-center">
          {filters.insuranceCompanyIds.map((id) => {
            const company = companies.find((c) => c.id === id);
            return company ? (
              <Badge key={id} variant="default" className="gap-1 cursor-pointer" onClick={() => toggleCompany(id)}>
                {company.name} <X className="size-3" />
              </Badge>
            ) : null;
          })}
          {filters.insuranceType && (
            <Badge variant="default" className="gap-1 cursor-pointer" onClick={() => toggleInsuranceType(filters.insuranceType!)}>
              {INSURANCE_TYPE_LABELS[filters.insuranceType]} <X className="size-3" />
            </Badge>
          )}
          {filters.categories.map((code) => {
            const cat = categories.find((c) => c.code === code);
            return (
              <Badge key={code} variant="default" className="gap-1 cursor-pointer" onClick={() => toggleCategory(code)}>
                {cat?.name ?? code} <X className="size-3" />
              </Badge>
            );
          })}
          {filters.contentTypes.map((type) => (
            <Badge key={type} variant="default" className="gap-1 cursor-pointer" onClick={() => toggleContentType(type)}>
              {CONTENT_TYPE_LABELS[type]} <X className="size-3" />
            </Badge>
          ))}
          <button
            className="text-xs text-muted-foreground hover:text-foreground transition-colors"
            onClick={clearAll}
          >
            전체 해제
          </button>
        </div>
      )}

      {/* Accordion 필터 그룹 */}
      <Accordion multiple defaultValue={[]}>
        {/* 보험사 */}
        <AccordionItem value="company" className="not-last:border-b-0">
          <AccordionTrigger className={triggerClass}>
            보험사{companyCount > 0 && ` (${companyCount})`}
          </AccordionTrigger>
          <AccordionContent className={contentClass}>
            <div className="flex gap-1.5 flex-wrap">
              {companies.map((company) => (
                <Badge
                  key={company.id}
                  variant={filters.insuranceCompanyIds.includes(company.id) ? 'default' : 'outline'}
                  className="shrink-0 cursor-pointer"
                  role="checkbox"
                  aria-checked={filters.insuranceCompanyIds.includes(company.id)}
                  onClick={() => toggleCompany(company.id)}
                >
                  {company.name}
                </Badge>
              ))}
            </div>
          </AccordionContent>
        </AccordionItem>

        {/* 생/손보 */}
        <AccordionItem value="insuranceType" className="not-last:border-b-0">
          <AccordionTrigger className={triggerClass}>
            생/손보{filters.insuranceType && ` (${INSURANCE_TYPE_LABELS[filters.insuranceType]})`}
          </AccordionTrigger>
          <AccordionContent className={contentClass}>
            <div className="flex gap-1.5">
              {(Object.entries(INSURANCE_TYPE_LABELS) as [InsuranceType, string][]).map(([type, label]) => (
                <Badge
                  key={type}
                  variant={filters.insuranceType === type ? 'default' : 'outline'}
                  className="shrink-0 cursor-pointer"
                  role="checkbox"
                  aria-checked={filters.insuranceType === type}
                  onClick={() => toggleInsuranceType(type)}
                >
                  {label}
                </Badge>
              ))}
            </div>
          </AccordionContent>
        </AccordionItem>

        {/* 상품 카테고리 */}
        <AccordionItem value="category" className="not-last:border-b-0">
          <AccordionTrigger className={triggerClass}>
            상품 카테고리{categoryCount > 0 && ` (${categoryCount})`}
          </AccordionTrigger>
          <AccordionContent className={contentClass}>
            <div className="flex gap-1.5 flex-wrap">
              {filteredCategories.map((cat) => (
                <Badge
                  key={cat.code}
                  variant={filters.categories.includes(cat.code) ? 'default' : 'outline'}
                  className="shrink-0 cursor-pointer"
                  role="checkbox"
                  aria-checked={filters.categories.includes(cat.code)}
                  onClick={() => toggleCategory(cat.code)}
                >
                  {cat.name}
                </Badge>
              ))}
            </div>
          </AccordionContent>
        </AccordionItem>

        {/* 내용 유형 */}
        <AccordionItem value="contentType" className="not-last:border-b-0">
          <AccordionTrigger className={triggerClass}>
            내용 유형{contentTypeCount > 0 && ` (${contentTypeCount})`}
          </AccordionTrigger>
          <AccordionContent className={contentClass}>
            <div className="flex gap-1.5 flex-wrap">
              {(Object.entries(CONTENT_TYPE_LABELS) as [ContentType, string][]).map(([type, label]) => (
                <Badge
                  key={type}
                  variant={filters.contentTypes.includes(type) ? 'default' : 'outline'}
                  className="shrink-0 cursor-pointer"
                  role="checkbox"
                  aria-checked={filters.contentTypes.includes(type)}
                  onClick={() => toggleContentType(type)}
                >
                  {label}
                </Badge>
              ))}
            </div>
          </AccordionContent>
        </AccordionItem>
      </Accordion>
    </section>
  );
}
