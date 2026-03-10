import {
  Accordion,
  AccordionItem,
  AccordionTrigger,
  AccordionContent,
} from '@/components/ui/accordion';
import type { SurrenderValue, PremiumExample, PriceIndex, IndexType } from '@/types/policy';
import { INDEX_TYPE_LABELS } from '@/types/policy';

interface SupplementarySectionProps {
  surrenderValues?: SurrenderValue[];
  premiumExamples?: PremiumExample[];
  priceIndices?: PriceIndex[];
}

export function SupplementarySection({
  surrenderValues,
  premiumExamples,
  priceIndices,
}: SupplementarySectionProps) {
  const hasData =
    (surrenderValues && surrenderValues.length > 0) ||
    (premiumExamples && premiumExamples.length > 0) ||
    (priceIndices && priceIndices.length > 0);

  if (!hasData) return null;

  return (
    <section>
      <h2 className="text-base font-semibold mb-2">보충 정보</h2>
      <Accordion>
        {surrenderValues && surrenderValues.length > 0 && (
          <AccordionItem value="surrender">
            <AccordionTrigger>해약환급금 예시</AccordionTrigger>
            <AccordionContent>
              <div className="overflow-x-auto">
                <table className="w-full text-sm" data-testid="surrender-table">
                  <thead>
                    <tr className="border-b">
                      <th className="py-1.5 px-2 text-left font-medium">경과기간</th>
                      <th className="py-1.5 px-2 text-right font-medium">납입보험료</th>
                      <th className="py-1.5 px-2 text-right font-medium">환급금</th>
                      <th className="py-1.5 px-2 text-right font-medium">환급률</th>
                    </tr>
                  </thead>
                  <tbody>
                    {surrenderValues.map((sv) => (
                      <tr key={sv.elapsedMonths} className="border-b last:border-0">
                        <td className="py-1.5 px-2">{sv.elapsedMonths}개월</td>
                        <td className="py-1.5 px-2 text-right">{sv.premiumPaid.toLocaleString()}원</td>
                        <td className="py-1.5 px-2 text-right">{sv.surrenderValue.toLocaleString()}원</td>
                        <td className="py-1.5 px-2 text-right">{sv.surrenderRate}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </AccordionContent>
          </AccordionItem>
        )}

        {premiumExamples && premiumExamples.length > 0 && (
          <AccordionItem value="premium">
            <AccordionTrigger>보험료 예시</AccordionTrigger>
            <AccordionContent>
              <div className="overflow-x-auto">
                <table className="w-full text-sm" data-testid="premium-table">
                  <thead>
                    <tr className="border-b">
                      <th className="py-1.5 px-2 text-left font-medium">성별</th>
                      <th className="py-1.5 px-2 text-right font-medium">나이</th>
                      <th className="py-1.5 px-2 text-right font-medium">보험기간</th>
                      <th className="py-1.5 px-2 text-right font-medium">납입기간</th>
                      <th className="py-1.5 px-2 text-right font-medium">월보험료</th>
                    </tr>
                  </thead>
                  <tbody>
                    {premiumExamples.map((pe, i) => (
                      <tr key={i} className="border-b last:border-0">
                        <td className="py-1.5 px-2">{pe.gender}</td>
                        <td className="py-1.5 px-2 text-right">{pe.age}세</td>
                        <td className="py-1.5 px-2 text-right">{pe.insurancePeriod}</td>
                        <td className="py-1.5 px-2 text-right">{pe.paymentPeriod}</td>
                        <td className="py-1.5 px-2 text-right">{pe.premium.toLocaleString()}원</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </AccordionContent>
          </AccordionItem>
        )}

        {priceIndices && priceIndices.length > 0 && (
          <AccordionItem value="price-index">
            <AccordionTrigger>가격지수</AccordionTrigger>
            <AccordionContent>
              <div className="overflow-x-auto">
                <table className="w-full text-sm" data-testid="price-index-table">
                  <thead>
                    <tr className="border-b">
                      <th className="py-1.5 px-2 text-left font-medium">성별</th>
                      <th className="py-1.5 px-2 text-left font-medium">유형</th>
                      <th className="py-1.5 px-2 text-right font-medium">지수</th>
                    </tr>
                  </thead>
                  <tbody>
                    {priceIndices.map((pi, i) => (
                      <tr key={i} className="border-b last:border-0">
                        <td className="py-1.5 px-2">{pi.gender}</td>
                        <td className="py-1.5 px-2">{INDEX_TYPE_LABELS[pi.indexType as IndexType] ?? pi.indexType}</td>
                        <td className="py-1.5 px-2 text-right">{pi.indexValue}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </AccordionContent>
          </AccordionItem>
        )}
      </Accordion>
    </section>
  );
}
