import { Card, CardContent, Stack, Typography } from '@mui/material';

export default function Panel({ title, subtitle, action, children }) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" sx={{ mb: 1 }}>
          <div>
            <Typography variant="subtitle1" fontWeight={700} component="h2">{title}</Typography>
            {subtitle && <Typography variant="caption" color="text.secondary">{subtitle}</Typography>}
          </div>
          {action}
        </Stack>
        {children}
      </CardContent>
    </Card>
  );
}
