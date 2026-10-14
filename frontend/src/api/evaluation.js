import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client.js';
import { tenderKeys } from './tenders.js';

const base = (tenderId) => `/api/v1/tenders/${tenderId}/evaluation`;
export const evaluationKey = (tenderId) => ['tenders', tenderId, 'evaluation'];

/** The tender's evaluation (or what is needed to set one up). Refreshes every 30 s. */
export function useEvaluation(tenderId, { enabled = true } = {}) {
  return useQuery({
    queryKey: evaluationKey(tenderId),
    queryFn: ({ signal }) => apiFetch(base(tenderId), { signal }),
    enabled: Boolean(tenderId) && enabled,
    refetchInterval: 30_000,
  });
}

function useEvaluationMutation(tenderId, fn) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (data) => {
      qc.setQueryData(evaluationKey(tenderId), data);
      // Closing stages, awards and terminations change the tender too.
      qc.invalidateQueries({ queryKey: tenderKeys.detail(tenderId) });
      qc.invalidateQueries({ queryKey: tenderKeys.history(tenderId) });
      qc.invalidateQueries({ queryKey: tenderKeys.all, exact: false, refetchType: 'none' });
    },
  });
}

const post = (tenderId, path, body) => apiFetch(`${base(tenderId)}${path}`, { method: 'POST', body: body ?? {} });
const put = (tenderId, path, body) => apiFetch(`${base(tenderId)}${path}`, { method: 'PUT', body });

export const useSetUpEvaluation = (tenderId) =>
  useEvaluationMutation(tenderId, (templateCode) => post(tenderId, '', { templateCode: templateCode || null }));

export const useLoadTemplate = (tenderId) =>
  useEvaluationMutation(tenderId, (templateCode) => post(tenderId, '/template', { templateCode }));

/** body: { templateCode?, passMarkPct, criteria: [{ stage, name, description, maxScore }] } */
export const useSaveCriteria = (tenderId) => useEvaluationMutation(tenderId, (body) => put(tenderId, '/criteria', body));

/** members: [{ userId, role }] */
export const useSaveCommittee = (tenderId) =>
  useEvaluationMutation(tenderId, (members) => put(tenderId, '/committee', { members }));

export const useAppoint = (tenderId) => useEvaluationMutation(tenderId, () => post(tenderId, '/appoint'));

/** body: { userId, role, reason } */
export const useChangeCommittee = (tenderId) =>
  useEvaluationMutation(tenderId, (body) => post(tenderId, '/committee-changes', body));

/** body: { conflict, declaration } */
export const useDeclare = (tenderId) => useEvaluationMutation(tenderId, (body) => post(tenderId, '/declaration', body));

/** body: { bidId, marks: [{ criterionId, passed?, score?, comment? }] } */
export const useSaveMarks = (tenderId) => useEvaluationMutation(tenderId, (body) => put(tenderId, '/marks', body));

export const useCloseStage = (tenderId) => useEvaluationMutation(tenderId, () => post(tenderId, '/close-stage'));

export const useSavePrice = (tenderId) =>
  useEvaluationMutation(tenderId, ({ bidId, evaluatedPriceMinor, note }) =>
    put(tenderId, `/bids/${bidId}/price`, { evaluatedPriceMinor, note }),
  );

export const useRejectBid = (tenderId) =>
  useEvaluationMutation(tenderId, ({ bidId, reason }) => post(tenderId, `/bids/${bidId}/reject`, { reason }));

/** body: { bidId, noAward, recommendation, deviationReason } */
export const useSubmitReport = (tenderId) => useEvaluationMutation(tenderId, (body) => post(tenderId, '/report', body));

export const useSign = (tenderId) => useEvaluationMutation(tenderId, (note) => post(tenderId, '/sign', { note }));

/** body: { decision: APPROVE | RETURN | TERMINATE, note } */
export const useDecide = (tenderId) => useEvaluationMutation(tenderId, (body) => post(tenderId, '/decision', body));
