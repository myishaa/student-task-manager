#!/usr/bin/env python3
"""Reproducible illustrative monthly list-price estimate for the CCD demo."""
from decimal import Decimal, ROUND_HALF_UP

HOURS_PER_MONTH = Decimal("730")
RATES = {
    "ec2_t3_small_hour": Decimal("0.0264"),
    "ec2_gp3_gb_month": Decimal("0.096"),
    "rds_db_t4g_micro_hour": Decimal("0.025"),
    "rds_gp3_gb_month": Decimal("0.138"),
    "public_ipv4_hour": Decimal("0.005"),
    "secrets_manager_secret_month": Decimal("0.40"),
    "ecr_storage_gb_month": Decimal("0.10"),
}
QUANTITIES = {
    "ec2_hours": HOURS_PER_MONTH,
    "ec2_root_volume_gb": Decimal("30"),
    "rds_hours": HOURS_PER_MONTH,
    "rds_storage_gb": Decimal("20"),
    "public_ipv4_hours": HOURS_PER_MONTH,
    "secrets": Decimal("1"),
    "ecr_retained_gb": Decimal("1"),
}


def money(value: Decimal) -> str:
    return "$" + str(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def main() -> None:
    lines = [
        ("EC2 t3.small", RATES["ec2_t3_small_hour"] * QUANTITIES["ec2_hours"]),
        ("EC2 gp3 root volume (30 GB)", RATES["ec2_gp3_gb_month"] * QUANTITIES["ec2_root_volume_gb"]),
        ("One public IPv4 address", RATES["public_ipv4_hour"] * QUANTITIES["public_ipv4_hours"]),
        ("RDS MySQL db.t4g.micro", RATES["rds_db_t4g_micro_hour"] * QUANTITIES["rds_hours"]),
        ("RDS gp3 storage (20 GB)", RATES["rds_gp3_gb_month"] * QUANTITIES["rds_storage_gb"]),
        ("One Secrets Manager secret", RATES["secrets_manager_secret_month"] * QUANTITIES["secrets"]),
    ]
    core = sum((amount for _, amount in lines), Decimal("0"))
    ecr = RATES["ecr_storage_gb_month"] * QUANTITIES["ecr_retained_gb"]
    print("Illustrative AWS Sydney monthly list-price estimate (USD, 730 hours)")
    for label, amount in lines:
        print(f"{label:<39} {money(amount)}")
    print(f"{'Core estimated total':<39} {money(core)}")
    print(f"{'ECR storage (assume 1 GB retained)':<39} {money(ecr)}")
    print(f"{'Total including ECR assumption':<39} {money(core + ecr)}")
    print(f"{'Additional worker node, compute only, 146 h':<39} "
          f"{money(RATES['ec2_t3_small_hour'] * Decimal('146'))}")
    print(f"{'Always-on extra worker, compute only':<39} "
          f"{money(RATES['ec2_t3_small_hour'] * HOURS_PER_MONTH)}")
    print(f"{'Modeled compute-only saving on that extra node':<39} "
          f"{money(RATES['ec2_t3_small_hour'] * (HOURS_PER_MONTH - Decimal('146')))}")


if __name__ == "__main__":
    main()
