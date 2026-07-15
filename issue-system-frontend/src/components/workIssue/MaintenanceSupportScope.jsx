import React from 'react';

const SUPPORT_ITEMS = [
  { key: 'smsStatus', label: 'SMS', color: 'bg-emerald-500' },
  { key: 'nmsStatus', label: 'NMS', color: 'bg-blue-500' },
];

const HIDDEN_VALUES = new Set(['X', '-', '미대상']);

function text(value) {
  return value == null ? '' : String(value).trim();
}

export function supportProgress(value) {
  const matches = [...text(value).matchAll(/(\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)/g)];
  if (!matches.length) return null;

  const totals = matches.reduce((result, match) => ({
    current: result.current + Number(match[1]),
    total: result.total + Number(match[2]),
  }), { current: 0, total: 0 });

  if (!totals.total) return null;
  return Math.min(100, Math.max(0, (totals.current / totals.total) * 100));
}

export default function MaintenanceSupportScope({ smsStatus, nmsStatus, renderValue = (value) => value }) {
  const values = { smsStatus, nmsStatus };
  const items = SUPPORT_ITEMS
    .map((item) => ({ ...item, value: text(values[item.key]) }))
    .filter((item) => item.value && !HIDDEN_VALUES.has(item.value.toUpperCase()));

  if (!items.length) return null;

  return (
    <div className="space-y-4">
      {items.map((item) => {
        const progress = supportProgress(item.value);

        return (
          <div key={item.key}>
            <div className="mb-1.5 flex items-start justify-between gap-4 leading-5">
              <span className="font-bold text-slate-800">{item.label}</span>
              <span className="whitespace-pre-line text-right font-medium text-slate-600">
                {renderValue(item.value)}
              </span>
            </div>
            {progress != null ? (
              <div
                aria-label={`${item.label} 지원 범위`}
                aria-valuemax={100}
                aria-valuemin={0}
                aria-valuenow={Math.round(progress)}
                className="h-1.5 overflow-hidden rounded-full bg-slate-200"
                role="progressbar"
              >
                <div className={`h-full rounded-full ${item.color}`} style={{ width: `${progress}%` }} />
              </div>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
