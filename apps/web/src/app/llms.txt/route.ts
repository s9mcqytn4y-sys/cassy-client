import { MARKETING_UPDATED } from "@/components/Marketing";
import { LLMS_BASE } from "@/lib/llms";
import { plansText } from "@/lib/llmsPlans";
import { loadPlans } from "@/lib/plans";

export const revalidate = 86400;

export async function GET() {
  const body = `${LLMS_BASE}\n\n${plansText(await loadPlans(), MARKETING_UPDATED)}\n`;
  return new Response(body, { headers: { "Content-Type": "text/plain; charset=utf-8" } });
}
