
# AMAZON S3 BUCKET
resource "aws_s3_bucket" "terraform_bucket" {
  bucket        = var.s3_bucket_name
  force_destroy = true

  tags = {
    Name = "terraform-test-bucket"
  }
}


# VIRTUAL MACHINE (EC2 Instance)
data "aws_ami" "cheapest_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-minimal-*-arm64"]
  }
}

resource "aws_instance" "test_vm" {
  ami           = data.aws_ami.cheapest_linux.id
  instance_type = "t4g.micro"

  root_block_device {
    volume_size = 8
    volume_type = "gp3"
  }

  tags = {
    Name = "Terraform-ARM64-VM"
  }
}


# AMAZON RDS DATABASE (PostgreSQL)
resource "aws_db_instance" "terraform_test_db" {
  allocated_storage    = var.db_allocated_storage
  engine               = "postgres"
  engine_version       = "15"
  instance_class       = var.db_instance_class
  db_name              = var.db_name
  username             = var.db_username
  password             = var.db_password
  parameter_group_name = "default.postgres15"

  tags = {
    Name = "terraform-test-db"
  }

  skip_final_snapshot    = true
}
