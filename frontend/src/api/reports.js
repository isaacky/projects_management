import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiFetch, downloadFile } from './client.js';
import { reportQuery } from '../domain/reports.js';

const BASE = '/api/v1/reports';

export function useReportCatalogue() {
  return useQuery({
    queryKey: ['reports', 'catalogue'],
    queryFn: ({ signal }) => apiFetch(BASE, { signal }),
    staleTime: 5 * 60_000,
  });
}

/** The filters for a report, which can depend on the values already chosen. */
export function useReportFilters(id, params) {
  return useQuery({
    queryKey: ['reports', id, 'filters', params],
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}/filters${reportQuery(params)}`, { signal }),
    enabled: Boolean(id),
    placeholderData: keepPreviousData,
  });
}

export function useReport(id, params, enabled = true) {
  return useQuery({
    queryKey: ['reports', id, 'result', params],
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}${reportQuery(params)}`, { signal }),
    enabled: Boolean(id) && enabled,
    placeholderData: keepPreviousData,
  });
}

export function downloadReport(id, params) {
  return downloadFile(`${BASE}/${id}/xlsx${reportQuery(params)}`, `${id}.xlsx`);
}
