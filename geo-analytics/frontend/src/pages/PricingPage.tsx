import { Container, Typography } from "@mui/material";

export default function PricingPage(): JSX.Element {
  return (
    <Container maxWidth="sm" sx={{ py: 4 }}>
      <Typography variant="h4" component="h1" fontWeight={600}>
        料金プラン
      </Typography>
      <Typography color="text.secondary" sx={{ mt: 2 }}>
        準備中です。
      </Typography>
    </Container>
  );
}
