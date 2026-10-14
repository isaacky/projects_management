import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiUpload } from './client.js';

const BASE = '/api/v1/tenders';

export const tenderKeys = {
  all: ['tenders'],
  list: (requisitionId) => ['tenders', 'list', requisitionId ?? null],
  detail: (id) => ['tenders', id],
  history: (id) => ['tenders', id, 'history'],
};

export function useTenders(requisitionId) {
  const qs = requisitionId ? `?requisitionId=${requisitionId}` : '';
  return useQuery({
    queryKey: tenderKeys.list(requisitionId),
    queryFn: ({ signal }) => apiFetch(`${BASE}${qs}`, { signal }),
  });
}

/** Refreshes every 30 s while open, so the closing countdown and status stay current. */
export function useTender(id) {
  return useQuery({
    queryKey: tenderKeys.detail(id),
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}`, { signal }),
    enabled: Boolean(id),
    refetchInterval: 30_000,
  });
}

export function useTenderHistory(id) {
  return useQuery({
    queryKey: tenderKeys.history(id),
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}/history`, { signal }),
    enabled: Boolean(id),
  });
}

function useTenderMutation(fn) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (detail) => {
      if (detail?.id) qc.setQueryData(tenderKeys.detail(detail.id), detail);
      qc.invalidateQueries({ queryKey: tenderKeys.all });
      qc.invalidateQueries({ queryKey: ['requisitions'] });
    },
  });
}

export const useCreateTender = () =>
  useTenderMutation((body) => apiFetch(BASE, { method: 'POST', body }));

export const useEditTender = (id) =>
  useTenderMutation((body) => apiFetch(`${BASE}/${id}`, { method: 'PUT', body }));

/** action: publish | extend | cancel | invitations | openings */
export const useTenderAction = (id) =>
  useTenderMutation(({ action, body }) => apiFetch(`${BASE}/${id}/${action}`, { method: 'POST', body }));

export const useUninvite = (id) =>
  useTenderMutation((supplierId) => apiFetch(`${BASE}/${id}/invitations/${supplierId}`, { method: 'DELETE' }));

export const useUploadDocument = (id) =>
  useTenderMutation((file) => {
    const form = new FormData();
    form.append('file', file);
    return apiUpload(`${BASE}/${id}/documents`, form);
  });

/** files: { single: File[], technical: File[], financial: File[] } */
export const useRecordBid = (id) =>
  useTenderMutation(({ supplierId, deliveryNote, files }) => {
    const form = new FormData();
    form.append('supplierId', supplierId);
    if (deliveryNote) form.append('deliveryNote', deliveryNote);
    Object.entries(files).forEach(([envelope, list]) => list.forEach((f) => form.append(envelope, f)));
    return apiUpload(`${BASE}/${id}/bids`, form);
  });

export const useWithdrawBid = (id) =>
  useTenderMutation(({ bidId, reason }) =>
    apiFetch(`${BASE}/${id}/bids/${bidId}/withdraw`, { method: 'POST', body: { reason } }),
  );

export const useReadOut = (id) =>
  useTenderMutation(({ bidId, priceMinor, note }) =>
    apiFetch(`${BASE}/${id}/bids/${bidId}/read-out`, { method: 'PUT', body: { priceMinor, note } }),
  );

export const useAddScans = (id) =>
  useTenderMutation(({ bidId, envelope, files }) => {
    const form = new FormData();
    files.forEach((f) => form.append('files', f));
    return apiUpload(`${BASE}/${id}/bids/${bidId}/scans?envelope=${envelope}`, form);
  });

export const documentUrl = (tenderId, docId) => `${BASE}/${tenderId}/documents/${docId}`;
export const bidFileUrl = (tenderId, bidId, fileId) => `${BASE}/${tenderId}/bids/${bidId}/files/${fileId}`;
