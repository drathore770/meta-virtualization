HOMEPAGE = "git://github.com/kubernetes/kubernetes"
SUMMARY = "Production-Grade Container Scheduling and Management"
DESCRIPTION = "Kubernetes is an open source system for managing containerized \
applications across multiple hosts, providing basic mechanisms for deployment, \
maintenance, and scaling of applications. \
"

PV = "v1.23-alpha+git${SRCREV_kubernetes}"
SRCREV_kubernetes = "f6331c74b673d3039240edc77cd66696bbefdd9c"
SRCREV_kubernetes-release = "7c1aa83dac555de6f05500911467b70aca4949f0"

SRC_URI = "git://github.com/kubernetes/kubernetes.git;branch=master;name=kubernetes \
           git://github.com/kubernetes/release;branch=master;name=kubernetes-release;destsuffix=git/release \
           file://0001-hack-lib-golang.sh-use-CC-from-environment.patch \
           file://0001-cross-don-t-build-tests-by-default.patch \
           file://0001-build-golang.sh-convert-remaining-go-calls-to-use.patch \
           file://0001-Makefile.generated_files-Fix-race-issue-for-installi.patch \
          "

DEPENDS += "rsync-native \
            coreutils-native \
            go-native \
           "

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://src/import/LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

GO_IMPORT = "import"

inherit systemd
inherit go
inherit goarch

COMPATIBLE_HOST = '(x86_64.*|arm.*|aarch64.*)-linux'

do_compile() {
	# link fixups for compilation
	rm -f ${S}/src/import/vendor/src
	ln -sf ./ ${S}/src/import/vendor/src

	export GOPATH="${S}/src/import/.gopath:${S}/src/import/vendor:${STAGING_DIR_TARGET}/${prefix}/local/go"
	cd ${S}/src/import

	# Build the host tools first, using the host compiler
	export GOARCH="${BUILD_GOARCH}"
	# Pass the needed cflags/ldflags so that cgo can find the needed headers files and libraries
	export CGO_ENABLED="1"
	export CFLAGS="${BUILD_CFLAGS}"
	export LDFLAGS="${BUILD_LDFLAGS}"
	export CGO_CFLAGS="${BUILD_CFLAGS}"
	# as of go 1.15.5, there are some flags the CGO doesn't like. Rather than
	# clearing them all, we sed away the ones we don't want.
	export CGO_LDFLAGS="$(echo ${BUILD_LDFLAGS} | sed 's/-Wl,-O1//g' | sed 's/-Wl,--dynamic-linker.*?( \|$\)//g')"
	export CC="${BUILD_CC}"
	export LD="${BUILD_LD}"

	make generated_files GO="go" KUBE_BUILD_PLATFORMS="${HOST_GOOS}/${BUILD_GOARCH}"

	# Build the target binaries
	export GOARCH="${TARGET_GOARCH}"
	# Pass the needed cflags/ldflags so that cgo can find the needed headers files and libraries
	export CGO_ENABLED="1"
	export CGO_CFLAGS="${CFLAGS} --sysroot=${STAGING_DIR_TARGET}"
	export CGO_LDFLAGS="${LDFLAGS} --sysroot=${STAGING_DIR_TARGET}"
	export CFLAGS=""
	export LDFLAGS=""
	export CC="${CC}"
	export LD="${LD}"
	export GOBIN=""

	# to limit what is built, use 'WHAT', i.e. make WHAT=cmd/kubelet
	make cross CGO_FLAGS=${CGO_FLAGS} GO=${GO} KUBE_BUILD_PLATFORMS=${GOOS}/${GOARCH} GOLDFLAGS=""
}

do_install() {
    install -d ${D}${bindir}
    install -d ${D}${systemd_unitdir}/system/
    install -d ${D}${systemd_unitdir}/system/kubelet.service.d/

    install -d ${D}${sysconfdir}/kubernetes/manifests/

    install -m 755 -D ${S}/src/import/_output/local/bin/${TARGET_GOOS}/${TARGET_GOARCH}/* ${D}/${bindir}

    install -m 0644 ${WORKDIR}/git/release/cmd/kubepkg/templates/latest/deb/kubelet/lib/systemd/system/kubelet.service ${D}${systemd_unitdir}/system/
    install -m 0644 ${WORKDIR}/git/release/cmd/kubepkg/templates/latest/deb/kubeadm/10-kubeadm.conf  ${D}${systemd_unitdir}/system/kubelet.service.d/
}

PACKAGES =+ "kubeadm kubectl kubelet kube-proxy ${PN}-misc"

ALLOW_EMPTY:${PN} = "1"
INSANE_SKIP:${PN} += "ldflags already-stripped"
INSANE_SKIP:${PN}-misc += "ldflags already-stripped"

# Note: we are explicitly *not* adding docker to the rdepends, since we allow
#       backends like cri-o to be used.
RDEPENDS:${PN} += "kubeadm \
                   kubectl \
                   kubelet \
                   cni"

RDEPENDS:kubeadm = "kubelet kubectl"
FILES:kubeadm = "${bindir}/kubeadm ${systemd_unitdir}/system/kubelet.service.d/*"

RDEPENDS:kubelet = "iptables socat util-linux ethtool iproute2 ebtables iproute2-tc"
FILES:kubelet = "${bindir}/kubelet ${systemd_unitdir}/system/kubelet.service ${sysconfdir}/kubernetes/manifests/"

SYSTEMD_PACKAGES = "${@bb.utils.contains('DISTRO_FEATURES','systemd','kubelet','',d)}"
SYSTEMD_SERVICE:kubelet = "${@bb.utils.contains('DISTRO_FEATURES','systemd','kubelet.service','',d)}"
SYSTEMD_AUTO_ENABLE:kubelet = "enable"

FILES:kubectl = "${bindir}/kubectl"
FILES:kube-proxy = "${bindir}/kube-proxy"
FILES:${PN}-misc = "${bindir}"


deltask compile_ptest_base
