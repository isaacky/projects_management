import PropTypes from 'prop-types';
import { Link as RouterLink, useNavigate } from 'react-router';
import { Button, Tooltip } from '@mui/material';
import { useContracts, useCreateContract } from '../../api/contracts.js';
import { isProcurement, useMe } from '../../api/me.js';

/** On an awarded tender: open its contract, or (procurement) prepare it. */
export default function TenderContractButton({ tender }) {
  const navigate = useNavigate();
  const { data: me } = useMe();
  const { data: contracts } = useContracts({ tenderId: tender.id });
  const create = useCreateContract();
  const live = (contracts ?? []).find((c) => c.status !== 'CANCELLED');
  if (live) {
    return (
      <Button color="inherit" size="small" component={RouterLink} to={`/contracts/${live.id}`}>
        Contract {live.number}
      </Button>
    );
  }
  if (!isProcurement(me)) return null;
  return (
    <Tooltip title={create.error?.message ?? ''}>
      <span>
        <Button
          color="inherit"
          size="small"
          loading={create.isPending}
          onClick={() => create.mutate({ tenderId: tender.id }, { onSuccess: (c) => navigate(`/contracts/${c.id}`) })}
        >
          Prepare contract
        </Button>
      </span>
    </Tooltip>
  );
}

TenderContractButton.propTypes = { tender: PropTypes.object.isRequired };
